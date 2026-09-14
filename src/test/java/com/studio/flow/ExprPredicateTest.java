package com.studio.flow;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 比较谓词回归（{@link Expr} 的 {@code @eq/@ne/@gt/@ge/@lt/@le}）。
 *
 * <p>这些谓词是剧本 {@code @if <名> >= <值> goto <label>} 的编译落点：
 * 编译器把它编成 {@code @plugin(select) | @ge(...) | 真分支 | 假分支 | @var(__flow_next)}，
 * 因此"缺变量按 0、非数值退化字符串比较、绝不报错"这三条口径必须钉死。</p>
 */
class ExprPredicateTest {

    /** 最小作用域：变量表 + 空节点/参数 */
    private static Expr.Scope scope(Map<String, String> vars) {
        return new Expr.Scope() {
            @Override
            public String var(String name, String def) {
                String v = vars.get(name);
                return v == null ? def : v;
            }

            @Override
            public void setVar(String name, String value) {
                vars.put(name, value);
            }

            @Override
            public String nodeProp(String id, String prop) {
                return "";
            }

            @Override
            public String param(String name) {
                return "";
            }

            @Override
            public String sourceNodeId() {
                return "";
            }
        };
    }

    private static Expr.Scope vars(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return scope(m);
    }

    private static String eval(String expr, Expr.Scope sc) {
        return Expr.resolve(expr, sc);
    }

    @Test
    void geOnExistingVar() {
        Expr.Scope sc = vars("chaos", "1");
        assertEquals("true", eval("@ge(@var(chaos), 1)", sc));
        assertEquals("true", eval("@ge(@var(chaos), 0)", sc));
        assertEquals("false", eval("@ge(@var(chaos), 2)", sc));
    }

    @Test
    void missingVarCountsAsZero() {
        Expr.Scope sc = vars();
        assertEquals("false", eval("@ge(@var(chaos), 1)", sc), "未赋值应按 0，0>=1 为假");
        assertEquals("true", eval("@ge(@var(chaos), 0)", sc));
        assertEquals("true", eval("@eq(@var(chaos), 0)", sc));
        assertEquals("false", eval("@gt(@var(chaos), 0)", sc));
    }

    @Test
    void allOperatorsWork() {
        Expr.Scope sc = vars("n", "2");
        assertEquals("true", eval("@gt(@var(n), 1)", sc));
        assertEquals("false", eval("@gt(@var(n), 2)", sc));
        assertEquals("true", eval("@ge(@var(n), 2)", sc));
        assertEquals("true", eval("@lt(@var(n), 3)", sc));
        assertEquals("false", eval("@lt(@var(n), 2)", sc));
        assertEquals("true", eval("@le(@var(n), 2)", sc));
        assertEquals("true", eval("@eq(@var(n), 2)", sc));
        assertEquals("false", eval("@ne(@var(n), 2)", sc));
    }

    @Test
    void numericComparisonBeatsLexicographic() {
        Expr.Scope sc = vars("n", "10");
        assertEquals("true", eval("@gt(@var(n), 9)", sc), "数值比较：10 > 9（不是字符串比较）");
        assertEquals("false", eval("@lt(@var(n), 9)", sc));
    }

    @Test
    void nonNumericFallsBackToStringCompare() {
        Expr.Scope sc = vars("song", "鲸");
        assertEquals("true", eval("@eq(@var(song), 鲸)", sc));
        assertEquals("true", eval("@ne(@var(song), 鳞)", sc));
        assertEquals("false", eval("@eq(@var(song), 鳞)", sc));
    }

    @Test
    void nestedExpressionsResolve() {
        Expr.Scope sc = vars("pieces", "9");
        assertEquals("true", eval("@ge(@int(@var(pieces)), 9)", sc));
        assertEquals("false", eval("@ge(@int(@var(pieces)), 10)", sc));
    }

    @Test
    void tooFewArgsIsFalseNotCrash() {
        Expr.Scope sc = vars("x", "1");
        assertEquals("false", eval("@ge(@var(x))", sc));
    }

    @Test
    void predicatesAreTruthyCompatible() {
        // select 插件用 truthy(in[0]) 判真：谓词返回值必须能被它正确解释
        Expr.Scope sc = vars("chaos", "1");
        assertEquals("true", eval("@ge(@var(chaos), 1)", sc));
        assertEquals("false", eval("@ge(@var(chaos), 5)", sc));
    }
}
