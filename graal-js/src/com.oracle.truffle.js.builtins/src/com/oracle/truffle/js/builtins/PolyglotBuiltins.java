/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.js.builtins;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.js.builtins.PolyglotBuiltinsFactory.PolyglotConstructNodeGen;
import com.oracle.truffle.js.builtins.PolyglotBuiltinsFactory.PolyglotCreateForeignDynamicObjectNodeGen;
import com.oracle.truffle.js.builtins.PolyglotBuiltinsFactory.PolyglotCreateForeignObjectNodeGen;
import com.oracle.truffle.js.builtins.PolyglotBuiltinsFactory.PolyglotEvalFileNodeGen;
import com.oracle.truffle.js.builtins.PolyglotBuiltinsFactory.PolyglotEvalNodeGen;
import com.oracle.truffle.js.builtins.PolyglotBuiltinsFactory.PolyglotExecuteNodeGen;
import com.oracle.truffle.js.builtins.PolyglotBuiltinsFactory.PolyglotExportNodeGen;
import com.oracle.truffle.js.builtins.PolyglotBuiltinsFactory.PolyglotGetSizeNodeGen;
import com.oracle.truffle.js.builtins.PolyglotBuiltinsFactory.PolyglotHasKeysNodeGen;
import com.oracle.truffle.js.builtins.PolyglotBuiltinsFactory.PolyglotHasSizeNodeGen;
import com.oracle.truffle.js.builtins.PolyglotBuiltinsFactory.PolyglotImportNodeGen;
import com.oracle.truffle.js.builtins.PolyglotBuiltinsFactory.PolyglotIsBoxedPrimitiveNodeGen;
import com.oracle.truffle.js.builtins.PolyglotBuiltinsFactory.PolyglotIsExecutableNodeGen;
import com.oracle.truffle.js.builtins.PolyglotBuiltinsFactory.PolyglotIsInstantiableNodeGen;
import com.oracle.truffle.js.builtins.PolyglotBuiltinsFactory.PolyglotIsNullNodeGen;
import com.oracle.truffle.js.builtins.PolyglotBuiltinsFactory.PolyglotKeysNodeGen;
import com.oracle.truffle.js.builtins.PolyglotBuiltinsFactory.PolyglotReadNodeGen;
import com.oracle.truffle.js.builtins.PolyglotBuiltinsFactory.PolyglotRemoveNodeGen;
import com.oracle.truffle.js.builtins.PolyglotBuiltinsFactory.PolyglotToJSValueNodeGen;
import com.oracle.truffle.js.builtins.PolyglotBuiltinsFactory.PolyglotToPolyglotValueNodeGen;
import com.oracle.truffle.js.builtins.PolyglotBuiltinsFactory.PolyglotUnboxValueNodeGen;
import com.oracle.truffle.js.builtins.PolyglotBuiltinsFactory.PolyglotWriteNodeGen;
import com.oracle.truffle.js.nodes.function.JSBuiltin;
import com.oracle.truffle.js.nodes.function.JSBuiltinNode;
import com.oracle.truffle.js.nodes.interop.ExportValueNode;
import com.oracle.truffle.js.nodes.interop.JSForeignToJSTypeNode;
import com.oracle.truffle.js.runtime.Errors;
import com.oracle.truffle.js.runtime.Evaluator;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSRealm;
import com.oracle.truffle.js.runtime.JSTruffleOptions;
import com.oracle.truffle.js.runtime.builtins.BuiltinEnum;
import com.oracle.truffle.js.runtime.builtins.JSArray;
import com.oracle.truffle.js.runtime.objects.Null;
import com.oracle.truffle.js.runtime.objects.Undefined;
import com.oracle.truffle.js.runtime.truffleinterop.JSInteropUtil;
import com.oracle.truffle.js.runtime.util.Pair;

public final class PolyglotBuiltins extends JSBuiltinsContainer.SwitchEnum<PolyglotBuiltins.Polyglot> {
    protected PolyglotBuiltins() {
        super(JSRealm.POLYGLOT_CLASS_NAME, Polyglot.class);
    }

    public enum Polyglot implements BuiltinEnum<Polyglot> {
        // external
        export(2),
        import_(1),
        eval(2);

        private final int length;

        Polyglot(int length) {
            this.length = length;
        }

        @Override
        public int getLength() {
            return length;
        }
    }

    @Override
    protected Object createNode(JSContext context, JSBuiltin builtin, boolean construct, boolean newTarget, Polyglot builtinEnum) {
        switch (builtinEnum) {
            case export:
                return PolyglotExportNodeGen.create(context, builtin, args().fixedArgs(2).createArgumentNodes(context));
            case import_:
                return PolyglotImportNodeGen.create(context, builtin, args().fixedArgs(1).createArgumentNodes(context));
            case eval:
                return PolyglotEvalNodeGen.create(context, builtin, args().fixedArgs(2).createArgumentNodes(context));
        }
        return null;
    }

    public static final class PolyglotInternalBuiltins extends JSBuiltinsContainer.SwitchEnum<PolyglotInternalBuiltins.PolyglotInternal> {
        protected PolyglotInternalBuiltins() {
            super(JSRealm.POLYGLOT_INTERNAL_CLASS_NAME, PolyglotInternal.class);
        }

        public enum PolyglotInternal implements BuiltinEnum<PolyglotInternal> {
            isExecutable(1),
            isBoxed(1),
            isNull(1),
            hasSize(1),
            read(2),
            write(3),
            unbox(1),
            construct(1),
            execute(1),
            getSize(1),
            remove(2),
            toJSValue(1),
            toPolyglotValue(1),
            keys(1),
            hasKeys(1),
            isInstantiable(1),
            evalFile(2), // under special flag

            createForeignObject(0) {
                @Override
                public boolean isAOTSupported() {
                    return false;
                }
            },
            createForeignDynamicObject(0) {
                @Override
                public boolean isAOTSupported() {
                    return false;
                }
            };

            private final int length;

            PolyglotInternal(int length) {
                this.length = length;
            }

            @Override
            public int getLength() {
                return length;
            }
        }

        @Override
        protected Object createNode(JSContext context, JSBuiltin builtin, boolean construct, boolean newTarget, PolyglotInternal builtinEnum) {
            switch (builtinEnum) {
                case isExecutable:
                    return PolyglotIsExecutableNodeGen.create(context, builtin, args().fixedArgs(1).createArgumentNodes(context));
                case isBoxed:
                    return PolyglotIsBoxedPrimitiveNodeGen.create(context, builtin, args().fixedArgs(1).createArgumentNodes(context));
                case isNull:
                    return PolyglotIsNullNodeGen.create(context, builtin, args().fixedArgs(1).createArgumentNodes(context));
                case isInstantiable:
                    return PolyglotIsInstantiableNodeGen.create(context, builtin, args().fixedArgs(1).createArgumentNodes(context));
                case hasSize:
                    return PolyglotHasSizeNodeGen.create(context, builtin, args().fixedArgs(1).createArgumentNodes(context));
                case read:
                    return PolyglotReadNodeGen.create(context, builtin, args().fixedArgs(2).createArgumentNodes(context));
                case write:
                    return PolyglotWriteNodeGen.create(context, builtin, args().fixedArgs(3).createArgumentNodes(context));
                case remove:
                    return PolyglotRemoveNodeGen.create(context, builtin, args().fixedArgs(2).createArgumentNodes(context));
                case unbox:
                    return PolyglotUnboxValueNodeGen.create(context, builtin, args().fixedArgs(1).createArgumentNodes(context));
                case construct:
                    return PolyglotConstructNodeGen.create(context, builtin, args().fixedArgs(1).varArgs().createArgumentNodes(context));
                case execute:
                    return PolyglotExecuteNodeGen.create(context, builtin, args().fixedArgs(1).varArgs().createArgumentNodes(context));
                case getSize:
                    return PolyglotGetSizeNodeGen.create(context, builtin, args().fixedArgs(1).createArgumentNodes(context));
                case keys:
                    return PolyglotKeysNodeGen.create(context, builtin, args().fixedArgs(1).createArgumentNodes(context));
                case toJSValue:
                    return PolyglotToJSValueNodeGen.create(context, builtin, args().fixedArgs(1).createArgumentNodes(context));
                case toPolyglotValue:
                    return PolyglotToPolyglotValueNodeGen.create(context, builtin, args().fixedArgs(1).createArgumentNodes(context));
                case hasKeys:
                    return PolyglotHasKeysNodeGen.create(context, builtin, args().fixedArgs(1).createArgumentNodes(context));
                case evalFile:
                    return PolyglotEvalFileNodeGen.create(context, builtin, args().fixedArgs(2).createArgumentNodes(context));
                case createForeignObject:
                    if (!JSTruffleOptions.SubstrateVM) {
                        return PolyglotCreateForeignObjectNodeGen.create(context, builtin, args().fixedArgs(0).createArgumentNodes(context));
                    }
                    break;
                case createForeignDynamicObject:
                    if (!JSTruffleOptions.SubstrateVM) {
                        return PolyglotCreateForeignDynamicObjectNodeGen.create(context, builtin, args().fixedArgs(0).createArgumentNodes(context));
                    }
                    break;
            }
            return null;
        }
    }

    abstract static class PolyglotExportNode extends JSBuiltinNode {
        @Child private ExportValueNode exportValue;

        PolyglotExportNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
            exportValue = ExportValueNode.create();
        }

        @Specialization
        protected Object doString(String identifier, Object value,
                        @Shared("interop") @CachedLibrary(limit = "3") InteropLibrary interop) {
            Object polyglotBindings;
            try {
                polyglotBindings = getContext().getRealm().getEnv().getPolyglotBindings();
            } catch (SecurityException e) {
                throw Errors.createError(e.getMessage(), e);
            }
            JSInteropUtil.writeMember(polyglotBindings, identifier, value, interop, exportValue, this);
            return value;
        }

        @Specialization(guards = {"!isString(identifier)"})
        protected Object doMaybeUnbox(TruffleObject identifier, Object value,
                        @Shared("interop") @CachedLibrary(limit = "3") InteropLibrary interop) {
            if (interop.isString(identifier)) {
                String unboxed;
                try {
                    unboxed = interop.asString(identifier);
                } catch (UnsupportedMessageException e) {
                    throw Errors.createTypeErrorUnboxException(identifier, e, this);
                }
                return doString(unboxed, value, interop);
            }
            return doInvalid(identifier, value);
        }

        @Specialization(guards = "!isString(identifier)")
        @TruffleBoundary
        protected Object doInvalid(Object identifier, @SuppressWarnings("unused") Object value) {
            throw Errors.createTypeErrorInvalidIdentifier(identifier);
        }
    }

    abstract static class PolyglotImportNode extends JSBuiltinNode {
        PolyglotImportNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected Object doString(String identifier,
                        @Shared("interop") @CachedLibrary(limit = "3") InteropLibrary interop,
                        @Shared("importValue") @Cached JSForeignToJSTypeNode importValueNode) {
            Object polyglotBindings;
            try {
                polyglotBindings = getContext().getRealm().getEnv().getPolyglotBindings();
            } catch (SecurityException e) {
                throw Errors.createError(e.getMessage(), e);
            }
            try {
                return importValueNode.executeWithTarget(interop.readMember(polyglotBindings, identifier));
            } catch (UnknownIdentifierException e) {
                throw Errors.createReferenceErrorNotDefined(identifier, this);
            } catch (UnsupportedMessageException e) {
                throw Errors.createTypeErrorInteropException(polyglotBindings, e, "readMember", identifier, this);
            }
        }

        @Specialization(guards = {"!isString(identifier)"})
        protected Object doMaybeUnbox(TruffleObject identifier,
                        @Shared("interop") @CachedLibrary(limit = "3") InteropLibrary interop,
                        @Shared("importValue") @Cached JSForeignToJSTypeNode importValueNode) {
            if (interop.isString(identifier)) {
                String unboxed;
                try {
                    unboxed = interop.asString(identifier);
                } catch (UnsupportedMessageException e) {
                    throw Errors.createTypeErrorUnboxException(identifier, e, this);
                }
                return doString(unboxed, interop, importValueNode);
            }
            return doInvalid(identifier);
        }

        @Specialization(guards = {"!isString(identifier)", "!isTruffleObject(identifier)"})
        @TruffleBoundary
        protected Object doInvalid(Object identifier) {
            throw Errors.createTypeErrorInvalidIdentifier(identifier);
        }
    }

    abstract static class PolyglotIsExecutableNode extends JSBuiltinNode {

        PolyglotIsExecutableNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected static boolean truffleObject(TruffleObject obj,
                        @CachedLibrary(limit = "3") InteropLibrary interop) {
            return interop.isExecutable(obj);
        }

        @Specialization(guards = "isJavaPrimitive(obj)")
        protected static boolean primitive(@SuppressWarnings("unused") Object obj) {
            return false;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isTruffleObject(obj)", "!isJavaPrimitive(obj)"})
        protected static boolean unsupported(Object obj) {
            return false;
        }
    }

    abstract static class PolyglotIsBoxedPrimitiveNode extends JSBuiltinNode {

        PolyglotIsBoxedPrimitiveNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization(limit = "3")
        protected static boolean truffleObject(TruffleObject obj,
                        @CachedLibrary("obj") InteropLibrary interop) {
            return interop.isBoolean(obj) || interop.isString(obj) || interop.isNumber(obj);
        }

        @Specialization(guards = "isJavaPrimitive(obj)")
        protected static boolean primitive(@SuppressWarnings("unused") Object obj) {
            return false;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isTruffleObject(obj)", "!isJavaPrimitive(obj)"})
        protected static boolean unsupported(Object obj) {
            return false;
        }
    }

    abstract static class PolyglotIsNullNode extends JSBuiltinNode {

        PolyglotIsNullNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected static boolean truffleObject(TruffleObject obj,
                        @CachedLibrary(limit = "3") InteropLibrary interop) {
            return interop.isNull(obj);
        }

        @Specialization(guards = "isJavaPrimitive(obj)")
        protected static boolean primitive(@SuppressWarnings("unused") Object obj) {
            return false;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isTruffleObject(obj)", "!isJavaPrimitive(obj)"})
        protected static boolean unsupported(Object obj) {
            return false;
        }
    }

    abstract static class PolyglotHasSizeNode extends JSBuiltinNode {

        PolyglotHasSizeNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected boolean truffleObject(TruffleObject obj,
                        @CachedLibrary(limit = "3") InteropLibrary interop) {
            return interop.hasArrayElements(obj);
        }

        @Specialization(guards = "isJavaPrimitive(obj)")
        protected boolean primitive(@SuppressWarnings("unused") Object obj) {
            return false;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isTruffleObject(obj)", "!isJavaPrimitive(obj)"})
        protected boolean unsupported(Object obj) {
            return false;
        }
    }

    abstract static class PolyglotReadNode extends JSBuiltinNode {

        PolyglotReadNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected Object member(TruffleObject obj, String name,
                        @Shared("importValue") @Cached("create()") JSForeignToJSTypeNode foreignConvert,
                        @Shared("interop") @CachedLibrary(limit = "3") InteropLibrary interop) {
            return JSInteropUtil.readMemberOrDefault(obj, name, Null.instance, interop, foreignConvert, this);
        }

        @Specialization
        protected Object arrayElementInt(TruffleObject obj, int index,
                        @Shared("importValue") @Cached("create()") JSForeignToJSTypeNode foreignConvert,
                        @Shared("interop") @CachedLibrary(limit = "3") InteropLibrary interop) {
            return JSInteropUtil.readArrayElementOrDefault(obj, index, Null.instance, interop, foreignConvert, this);
        }

        @Specialization(guards = "isNumber(index)", replaces = "arrayElementInt")
        protected Object arrayElement(TruffleObject obj, Number index,
                        @Shared("importValue") @Cached("create()") JSForeignToJSTypeNode foreignConvert,
                        @Shared("interop") @CachedLibrary(limit = "3") InteropLibrary interop) {
            return JSInteropUtil.readArrayElementOrDefault(obj, index.longValue(), Null.instance, interop, foreignConvert, this);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isString(key)", "!isNumber(key)"})
        protected Object unsupportedKey(TruffleObject obj, Object key,
                        @Shared("importValue") @Cached("create()") JSForeignToJSTypeNode foreignConvert,
                        @Shared("interop") @CachedLibrary(limit = "3") InteropLibrary interop,
                        @CachedLibrary(limit = "3") InteropLibrary keyInterop) {
            try {
                if (keyInterop.isString(key)) {
                    return member(obj, keyInterop.asString(key), foreignConvert, interop);
                } else if (keyInterop.fitsInInt(key)) {
                    return arrayElement(obj, keyInterop.asInt(key), foreignConvert, interop);
                }
            } catch (UnsupportedMessageException e) {
                throw Errors.createTypeErrorUnboxException(obj, e, this);
            }
            return Null.instance;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isTruffleObject(obj)")
        protected boolean unsupported(Object obj, Object name) {
            throw Errors.createTypeErrorNotATruffleObject("read");
        }
    }

    abstract static class PolyglotWriteNode extends JSBuiltinNode {

        PolyglotWriteNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected Object member(TruffleObject obj, String name, Object value,
                        @Shared("exportValue") @Cached ExportValueNode exportValue,
                        @Shared("interop") @CachedLibrary(limit = "3") InteropLibrary interop) {
            Object convertedValue = exportValue.execute(value);
            try {
                interop.writeMember(obj, name, convertedValue);
                return convertedValue;
            } catch (UnknownIdentifierException e) {
                return Null.instance;
            } catch (UnsupportedMessageException | UnsupportedTypeException e) {
                throw Errors.createTypeErrorInteropException(obj, e, "writeMember", name, this);
            }
        }

        @Specialization
        protected Object arrayElementInt(TruffleObject obj, int index, Object value,
                        @Shared("exportValue") @Cached ExportValueNode exportValue,
                        @Shared("interop") @CachedLibrary(limit = "3") InteropLibrary interop) {
            Object convertedValue = exportValue.execute(value);
            try {
                interop.writeArrayElement(obj, index, convertedValue);
                return convertedValue;
            } catch (InvalidArrayIndexException e) {
                return Null.instance;
            } catch (UnsupportedMessageException | UnsupportedTypeException e) {
                throw Errors.createTypeErrorInteropException(obj, e, "writeArrayElement", index, this);
            }
        }

        @Specialization(guards = "isNumber(index)", replaces = "arrayElementInt")
        protected Object arrayElement(TruffleObject obj, Number index, Object value,
                        @Shared("exportValue") @Cached ExportValueNode exportValue,
                        @Shared("interop") @CachedLibrary(limit = "3") InteropLibrary interop) {
            Object convertedValue = exportValue.execute(value);
            try {
                interop.writeArrayElement(obj, index.longValue(), convertedValue);
                return convertedValue;
            } catch (InvalidArrayIndexException e) {
                return Null.instance;
            } catch (UnsupportedMessageException | UnsupportedTypeException e) {
                throw Errors.createTypeErrorInteropException(obj, e, "writeArrayElement", index, this);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isString(key)", "!isNumber(key)"})
        protected Object unsupportedKey(TruffleObject obj, Object key, Object value,
                        @Shared("exportValue") @Cached ExportValueNode exportValue,
                        @Shared("interop") @CachedLibrary(limit = "3") InteropLibrary interop,
                        @CachedLibrary(limit = "3") InteropLibrary keyInterop) {
            try {
                if (keyInterop.isString(key)) {
                    return member(obj, keyInterop.asString(key), value, exportValue, interop);
                } else if (keyInterop.fitsInInt(key)) {
                    return arrayElement(obj, keyInterop.asInt(key), value, exportValue, interop);
                }
            } catch (UnsupportedMessageException e) {
                throw Errors.createTypeErrorUnboxException(obj, e, this);
            }
            return Null.instance;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isTruffleObject(obj)")
        protected boolean unsupported(Object obj, Object name, Object value) {
            throw Errors.createTypeErrorNotATruffleObject("write");
        }
    }

    abstract static class PolyglotRemoveNode extends JSBuiltinNode {

        PolyglotRemoveNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected boolean member(TruffleObject obj, String name,
                        @Shared("interop") @CachedLibrary(limit = "3") InteropLibrary interop) {
            try {
                interop.removeMember(obj, name);
                return true;
            } catch (UnknownIdentifierException e) {
                return false;
            } catch (UnsupportedMessageException e) {
                throw Errors.createTypeErrorInteropException(obj, e, "removeMember", name, this);
            }
        }

        @Specialization
        protected boolean arrayElementInt(TruffleObject obj, int index,
                        @Shared("interop") @CachedLibrary(limit = "3") InteropLibrary interop) {
            try {
                interop.removeArrayElement(obj, index);
                return true;
            } catch (InvalidArrayIndexException e) {
                return false;
            } catch (UnsupportedMessageException e) {
                throw Errors.createTypeErrorInteropException(obj, e, "removeArrayElement", index, this);
            }
        }

        @Specialization(guards = "isNumber(index)", replaces = "arrayElementInt")
        protected boolean arrayElement(TruffleObject obj, Number index,
                        @Shared("interop") @CachedLibrary(limit = "3") InteropLibrary interop) {
            try {
                interop.removeArrayElement(obj, index.longValue());
                return true;
            } catch (InvalidArrayIndexException e) {
                return false;
            } catch (UnsupportedMessageException e) {
                throw Errors.createTypeErrorInteropException(obj, e, "removeArrayElement", index, this);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isString(key)", "!isNumber(key)"})
        protected Object unsupportedKey(TruffleObject obj, Object key,
                        @Shared("interop") @CachedLibrary(limit = "3") InteropLibrary interop,
                        @CachedLibrary(limit = "3") InteropLibrary keyInterop) {
            try {
                if (keyInterop.isString(key)) {
                    return member(obj, keyInterop.asString(key), interop);
                } else if (keyInterop.fitsInInt(key)) {
                    return arrayElement(obj, keyInterop.asInt(key), interop);
                }
            } catch (UnsupportedMessageException e) {
                throw Errors.createTypeErrorUnboxException(obj, e, this);
            }
            return Null.instance;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isTruffleObject(obj)")
        protected boolean unsupported(Object obj, Object key) {
            throw Errors.createTypeErrorNotATruffleObject("remove");
        }
    }

    abstract static class PolyglotUnboxValueNode extends JSBuiltinNode {

        PolyglotUnboxValueNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected Object truffleObject(TruffleObject obj,
                        @CachedLibrary(limit = "1") InteropLibrary interop) {
            Object unboxed = JSInteropUtil.toPrimitiveOrDefault(obj, obj, interop, this);
            if (unboxed == obj) {
                throw Errors.createTypeErrorNotATruffleObject("unbox");
            }
            return unboxed;
        }

        @Specialization(guards = "isJavaPrimitive(obj)")
        protected Object primitive(Object obj) {
            // identity function
            return obj;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isTruffleObject(obj)", "!isJavaPrimitive(obj)"})
        protected boolean unsupported(Object obj) {
            throw Errors.createTypeErrorNotATruffleObject("unbox");
        }
    }

    abstract static class PolyglotExecuteNode extends JSBuiltinNode {

        PolyglotExecuteNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected Object execute(TruffleObject obj, Object[] arguments,
                        @Cached ExportValueNode exportValue,
                        @CachedLibrary(limit = "3") InteropLibrary interop) {
            Object target = exportValue.execute(obj);
            Object[] convertedArgs = new Object[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                convertedArgs[i] = exportValue.execute(arguments[i]);
            }
            try {
                return interop.execute(target, convertedArgs);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw Errors.createTypeErrorInteropException(obj, e, "execute", this);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isTruffleObject(obj)")
        protected boolean unsupported(Object obj, Object[] arguments) {
            throw Errors.createTypeErrorNotATruffleObject("execute");
        }
    }

    abstract static class PolyglotConstructNode extends JSBuiltinNode {

        PolyglotConstructNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected Object doNew(TruffleObject obj, Object[] arguments,
                        @Cached ExportValueNode exportValue,
                        @CachedLibrary(limit = "3") InteropLibrary interop) {
            Object target = exportValue.execute(obj);
            Object[] convertedArgs = new Object[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                convertedArgs[i] = exportValue.execute(arguments[i]);
            }
            try {
                return interop.instantiate(target, convertedArgs);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw Errors.createTypeErrorInteropException(obj, e, "instantiate", this);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isTruffleObject(obj)")
        protected boolean unsupported(Object obj, Object[] arguments) {
            throw Errors.createTypeErrorNotATruffleObject("construct");
        }
    }

    abstract static class PolyglotGetSizeNode extends JSBuiltinNode {

        PolyglotGetSizeNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected Object getSize(TruffleObject obj,
                        @CachedLibrary(limit = "3") InteropLibrary interop) {
            try {
                return interop.getArraySize(obj);
            } catch (UnsupportedMessageException e) {
                return Null.instance;
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isTruffleObject(obj)")
        protected boolean unsupported(Object obj) {
            throw Errors.createTypeErrorNotATruffleObject("getSize");
        }
    }

    abstract static class PolyglotEvalBaseNode extends JSBuiltinNode {

        protected final ConditionProfile isValid = ConditionProfile.createBinaryProfile();

        PolyglotEvalBaseNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        protected Pair<String, String> getLanguageIdAndMimeType(String languageIdOrMimeType) {
            String languageId = languageIdOrMimeType;
            String mimeType = null;
            if (languageIdOrMimeType.indexOf('/') >= 0) {
                String language = Source.findLanguage(languageIdOrMimeType);
                if (language != null) {
                    languageId = language;
                    mimeType = languageIdOrMimeType;
                }
            }
            return new Pair<>(languageId, mimeType);
        }
    }

    abstract static class PolyglotEvalNode extends PolyglotEvalBaseNode {

        PolyglotEvalNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"language.equals(cachedLanguage)"}, limit = "1")
        @TruffleBoundary
        protected Object evalCachedLanguage(String language, String source,
                        @Cached("language") String cachedLanguage,
                        @Cached("getLanguageIdAndMimeType(language)") Pair<String, String> languagePair) {
            return evalStringIntl(source, languagePair.getFirst(), languagePair.getSecond());
        }

        @Specialization(replaces = "evalCachedLanguage")
        @TruffleBoundary
        protected Object evalString(String language, String source) {
            Pair<String, String> pair = getLanguageIdAndMimeType(language);
            return evalStringIntl(source, pair.getFirst(), pair.getSecond());
        }

        private Object evalStringIntl(String sourceText, String languageId, String mimeType) {
            CompilerAsserts.neverPartOfCompilation();
            getContext().checkEvalAllowed();
            Source source = Source.newBuilder(languageId, sourceText, Evaluator.EVAL_SOURCE_NAME).mimeType(mimeType).build();

            CallTarget callTarget;
            try {
                callTarget = getContext().getRealm().getEnv().parsePublic(source);
            } catch (Exception e) {
                throw Errors.createError(e.getMessage());
            }

            return callTarget.call();
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isString(languageId) || !isString(source)")
        protected Object eval(Object languageId, Object source) {
            throw Errors.createTypeError("Expected arguments: (String languageId, String sourceCode)");
        }
    }

    abstract static class PolyglotEvalFileNode extends PolyglotEvalBaseNode {

        PolyglotEvalFileNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"language.equals(cachedLanguage)"}, limit = "1")
        @TruffleBoundary
        protected Object evalFileCachedLanguage(String language, String file,
                        @Cached("language") String cachedLanguage,
                        @Cached("getLanguageIdAndMimeType(language)") Pair<String, String> languagePair) {
            return evalFileIntl(file, languagePair.getFirst(), languagePair.getSecond());
        }

        @Specialization(replaces = "evalFileCachedLanguage")
        @TruffleBoundary
        protected Object evalFileString(String language, String file) {
            Pair<String, String> pair = getLanguageIdAndMimeType(language);
            return evalFileIntl(file, pair.getFirst(), pair.getSecond());
        }

        private Object evalFileIntl(String fileName, String languageId, String mimeType) {
            CompilerAsserts.neverPartOfCompilation();
            TruffleLanguage.Env env = getContext().getRealm().getEnv();
            Source source;
            try {
                source = Source.newBuilder(languageId, env.getTruffleFile(fileName)).mimeType(mimeType).build();
            } catch (AccessDeniedException e) {
                throw Errors.createError("Cannot evaluate file " + fileName + ": permission denied");
            } catch (NoSuchFileException e) {
                throw Errors.createError("Cannot evaluate file " + fileName + ": no such file");
            } catch (IOException | SecurityException e) {
                throw Errors.createError("Cannot evaluate file: " + e.getMessage());
            }

            CallTarget callTarget;
            try {
                callTarget = env.parsePublic(source);
            } catch (Exception e) {
                throw Errors.createError(e.getMessage(), e);
            }

            return callTarget.call();
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isString(languageId) || !isString(fileName)")
        protected Object eval(Object languageId, Object fileName) {
            throw Errors.createTypeError("Expected arguments: (String languageId, String fileName)");
        }
    }

    abstract static class PolyglotHasKeysNode extends JSBuiltinNode {

        PolyglotHasKeysNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected boolean hasKeys(TruffleObject obj,
                        @CachedLibrary(limit = "3") InteropLibrary interop) {
            return interop.hasMembers(obj);
        }

        @Specialization(guards = "!isTruffleObject(obj)")
        protected boolean unsupported(@SuppressWarnings("unused") Object obj) {
            return false;
        }
    }

    abstract static class PolyglotKeysNode extends JSBuiltinNode {

        PolyglotKeysNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @TruffleBoundary
        @Specialization
        protected Object keys(TruffleObject obj) {
            return JSArray.createConstantObjectArray(getContext(), JSInteropUtil.keys(obj).toArray());
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isTruffleObject(obj)")
        protected boolean unsupported(Object obj) {
            throw Errors.createTypeErrorNotATruffleObject("keys");
        }
    }

    abstract static class PolyglotIsInstantiableNode extends JSBuiltinNode {

        PolyglotIsInstantiableNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected static boolean isInstantiable(TruffleObject obj,
                        @CachedLibrary(limit = "3") InteropLibrary interop) {
            return interop.isInstantiable(obj);
        }

        @Specialization(guards = "!isTruffleObject(obj)")
        protected static boolean unsupported(@SuppressWarnings("unused") Object obj) {
            return false;
        }
    }

    /**
     * This node exists for debugging purposes. You can call Interop.createForeignObject() from
     * JavaScript code to create a {@link TruffleObject}. It is used to simplify testing interop
     * features in JavaScript code.
     *
     */
    abstract static class PolyglotCreateForeignObjectNode extends JSBuiltinNode {

        private static Class<?> testMapClass;

        PolyglotCreateForeignObjectNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        @TruffleBoundary
        protected Object createForeignObject() {
            if (!JSTruffleOptions.SubstrateVM) {
                try {
                    if (testMapClass == null) {
                        testMapClass = Class.forName("com.oracle.truffle.js.test.polyglot.ForeignTestMap");
                    }
                    return getContext().getRealm().getEnv().asGuestValue(testMapClass.newInstance());
                } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
                    throw Errors.createTypeError("cannot test with ForeignTestMap: " + e.getMessage());
                }
            } else {
                return Undefined.instance;
            }
        }
    }

    /**
     * This node exists for debugging purposes. You can call Interop.createForeignDynamicObject()
     * from JavaScript code to create a {@link DynamicObject}. It is used to simplify testing
     * interop features in JavaScript code.
     *
     */
    abstract static class PolyglotCreateForeignDynamicObjectNode extends JSBuiltinNode {

        private static Class<?> testMapClass;

        PolyglotCreateForeignDynamicObjectNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        @TruffleBoundary
        protected Object createForeignDynamicObject() {
            if (!JSTruffleOptions.SubstrateVM) {
                try {
                    if (testMapClass == null) {
                        testMapClass = Class.forName("com.oracle.truffle.js.test.polyglot.ForeignDynamicObject");
                    }
                    Method createNew = testMapClass.getMethod("createNew");
                    return createNew.invoke(null);
                } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                    throw Errors.createTypeError("cannot test with ForeignDynamicObject: " + e.getMessage());
                }
            } else {
                return Undefined.instance;
            }
        }
    }

    /**
     * Forces the conversion of an (potential) interop value to a JavaScript compliant value. In
     * addition to the conversions forced at the language boundary anyway (e.g., Java primitive
     * types like short or float that are not supported by JavaScript), this operation also converts
     * Nullish interop values to the JavaScript null value, and unboxes boxed TruffleObjects.
     *
     */
    abstract static class PolyglotToJSValueNode extends JSBuiltinNode {
        PolyglotToJSValueNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected final Object toJSValue(TruffleObject obj,
                        @CachedLibrary(limit = "1") InteropLibrary interop) {
            return JSInteropUtil.toPrimitiveOrDefault(obj, obj, interop, this);
        }

        @Specialization(guards = "!isTruffleObject(obj)")
        protected static Object toJSValue(Object obj) {
            return obj;
        }
    }

    /**
     * Forces the conversion of a JavaScript value to a value compliant with Interop semantics. This
     * is done automatically at the language boundary and should rarely be necessary to be triggered
     * by user code.
     */
    abstract static class PolyglotToPolyglotValueNode extends JSBuiltinNode {
        @Child private ExportValueNode exportValueNode;

        PolyglotToPolyglotValueNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
            this.exportValueNode = ExportValueNode.create();
        }

        @Specialization
        protected Object execute(Object value) {
            return exportValueNode.execute(value);
        }
    }
}
