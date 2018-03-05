/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package com.oracle.truffle.js.test.instrumentation;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.oracle.truffle.js.nodes.instrumentation.JSTags.FunctionCallExpressionTag;
import com.oracle.truffle.js.nodes.instrumentation.JSTags.LiteralExpressionTag;
import com.oracle.truffle.js.nodes.instrumentation.JSTags.ReadElementExpressionTag;
import com.oracle.truffle.js.nodes.instrumentation.JSTags.ReadPropertyExpressionTag;
import com.oracle.truffle.js.nodes.instrumentation.JSTags.WritePropertyExpressionTag;
import com.oracle.truffle.js.runtime.builtins.JSFunction;
import com.oracle.truffle.js.runtime.objects.Undefined;

public class CallAccessTest extends FineGrainedAccessTest {

    @Test
    public void callOneArg() {
        evalAllTags("function foo(a) {}; foo(42);");

        assertEngineInit();

        // declaration
        assertGlobalFunctionExpressionDeclaration("foo");

        // foo(1)
        enter(FunctionCallExpressionTag.class, (e, call) -> {
            // target (which is undefined in this case) and function
            enter(LiteralExpressionTag.class).exit(assertReturnValue(Undefined.instance));
            call.input(assertUndefinedInput);
            // read 'foo' from the global object
            enter(ReadPropertyExpressionTag.class).input(assertGlobalObjectInput).exit();
            call.input(assertJSFunctionInput);
            // one argument
            enter(LiteralExpressionTag.class).exit(assertReturnValue(42));
            call.input(42);
        }).exit();
    }

    @Test
    public void callTwoArgs() {
        evalAllTags("function foo(a,b) {}; foo(42,24);");

        assertEngineInit();

        // declaration
        assertGlobalFunctionExpressionDeclaration("foo");

        // foo(1)
        enter(FunctionCallExpressionTag.class, (e, call) -> {
            // tead the target for 'foo', which is undefined
            enter(LiteralExpressionTag.class).exit(assertReturnValue(Undefined.instance));
            call.input(assertUndefinedInput);
            enter(ReadPropertyExpressionTag.class).input(assertGlobalObjectInput).exit();
            // target (which is undefined in this case) and function
            call.input(assertJSFunctionInput);
            enter(LiteralExpressionTag.class).exit(assertReturnValue(42));
            call.input(42);
            enter(LiteralExpressionTag.class).exit(assertReturnValue(24));
            call.input(24);
        }).exit();
    }

    @Test
    public void methodCall() {
        evalAllTags("var foo = {x:function foo(a,b) {}}; foo.x(42,24);");

        assertEngineInit();

        // var foo = ...
        enter(WritePropertyExpressionTag.class, (e, write) -> {
            assertAttribute(e, KEY, "foo");
            write.input(assertJSObjectInput);

            enter(LiteralExpressionTag.class, (e1, literal) -> {
                assertAttribute(e1, TYPE, LiteralExpressionTag.Type.ObjectLiteral.name());
                enter(LiteralExpressionTag.class, (e2) -> {
                    assertAttribute(e2, TYPE, LiteralExpressionTag.Type.FunctionLiteral.name());
                }).exit();
                literal.input(assertJSFunctionInput);
            }).exit();

            write.input(assertJSObjectInput);
        }).exit();

        // x.foo(1)
        enter(FunctionCallExpressionTag.class, (e, call) -> {
            // read 'foo' from global
            enter(ReadPropertyExpressionTag.class, (e1, prop) -> {
                assertAttribute(e1, KEY, "foo");
                prop.input(assertGlobalObjectInput);
            }).exit();
            // 1st argument to function is target
            call.input(assertJSObjectInput);
            // 2nd argument is the function itself
            enter(ReadPropertyExpressionTag.class, assertPropertyReadName("x")).input(assertJSObjectInput).exit();
            call.input(assertJSFunctionInput);
            // arguments
            enter(LiteralExpressionTag.class).exit(assertReturnValue(42));
            call.input(42);
            enter(LiteralExpressionTag.class).exit(assertReturnValue(24));
            call.input(24);
        }).exit();
    }

    @Test
    public void methodCallOneArg() {
        evalAllTags("var foo = {x:function foo(a,b) {}}; foo.x(42);");

        assertEngineInit();

        // var foo = ...
        enter(WritePropertyExpressionTag.class, (e, write) -> {
            assertAttribute(e, KEY, "foo");
            write.input(assertJSObjectInput);

            enter(LiteralExpressionTag.class, (e1, literal) -> {
                assertAttribute(e1, TYPE, LiteralExpressionTag.Type.ObjectLiteral.name());
                enter(LiteralExpressionTag.class, (e2) -> {
                    assertAttribute(e2, TYPE, LiteralExpressionTag.Type.FunctionLiteral.name());
                }).exit();
                literal.input(assertJSFunctionInput);
            }).exit();

            write.input(assertJSObjectInput);
        }).exit();

        // x.foo(1)
        enter(FunctionCallExpressionTag.class, (e, call) -> {
            // read 'foo' from global
            enter(ReadPropertyExpressionTag.class, (e1, prop) -> {
                assertAttribute(e1, KEY, "foo");
                prop.input(assertGlobalObjectInput);
            }).exit();
            // 1st argument to function is target
            call.input(assertJSObjectInput);
            // 2nd argument is the function itself
            enter(ReadPropertyExpressionTag.class, assertPropertyReadName("x")).input().exit();
            call.input(assertJSFunctionInput);
            // arguments
            enter(LiteralExpressionTag.class).exit(assertReturnValue(42));
            call.input(42);
        }).exit();
    }

    @Test
    public void methodCallElementArg() {
        evalAllTags("var a = {x:[function(){}]}; a.x[0](42);");

        assertEngineInit();

        // var a = ...
        enter(WritePropertyExpressionTag.class, (e, write) -> {
            assertAttribute(e, KEY, "a");
            write.input(assertGlobalObjectInput);

            enter(LiteralExpressionTag.class, (e1, oblit) -> {
                assertAttribute(e1, TYPE, LiteralExpressionTag.Type.ObjectLiteral.name());
                enter(LiteralExpressionTag.class, (e2, arrlit) -> {
                    assertAttribute(e2, TYPE, LiteralExpressionTag.Type.ArrayLiteral.name());
                    enter(LiteralExpressionTag.class, (e3) -> {
                        assertAttribute(e3, TYPE, LiteralExpressionTag.Type.FunctionLiteral.name());
                    }).exit();
                    arrlit.input(assertJSFunctionInput);
                }).exit();
                oblit.input(assertJSArrayInput);
            }).exit();
            write.input(assertJSObjectInput);
        }).exit();

        // a.x[0](42)
        enter(FunctionCallExpressionTag.class, (e, call) -> {
            // read 'a.x' from global
            enter(ReadPropertyExpressionTag.class, (e1, prop) -> {
                assertAttribute(e1, KEY, "x");
                enter(ReadPropertyExpressionTag.class, (e2, p2) -> {
                    assertAttribute(e2, KEY, "a");
                    p2.input(assertGlobalObjectInput);
                }).exit();
                prop.input(assertJSObjectInput);
            }).exit();
            // 1st argument is an array (i.e., target)
            call.input(assertJSArrayInput);
            // 2nd argument is the function itself

            enter(ReadElementExpressionTag.class, (e1, el) -> {
                el.input(assertJSArrayInput);
                enter(LiteralExpressionTag.class).exit(assertReturnValue(0));
                el.input(0);
            }).exit();

            call.input(assertJSFunctionInput);
            // arguments
            enter(LiteralExpressionTag.class).exit(assertReturnValue(42));
            call.input(42);
            // 'undefined' is the return value from the function call.
            enter(LiteralExpressionTag.class).exit(assertReturnValue(Undefined.instance));
        }).exit();

    }

    @Test
    public void newTest() {
        evalWithTag("function A() {}; var a = {x:function(){return 1;}}; new A(a.x(), a.x());", FunctionCallExpressionTag.class);

        enter(FunctionCallExpressionTag.class, (e, call) -> {
            call.input(assertJSFunctionInput);
            enter(FunctionCallExpressionTag.class).input().input().exit();
            call.input(1);
            enter(FunctionCallExpressionTag.class).input().input().exit();
            call.input(1);

        }).exit((r) -> {
            Object[] vals = (Object[]) r.val;
            assertTrue(vals[2].equals(1));
            assertTrue(vals[3].equals(1));
            // should be the function instead of null
            assertTrue(JSFunction.isJSFunction(vals[1]));
        });
    }

}