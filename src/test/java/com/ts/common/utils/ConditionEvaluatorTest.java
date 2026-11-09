package com.ts.common.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class ConditionEvaluatorTest {

    private Date date(String yyyyMMdd) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(yyyyMMdd);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ==================== 字段计算 ====================

    @Test
    void testYear() {
        assertTrue(eval("YEAR=2026", "2026-05-10"));
        assertFalse(eval("YEAR=2025", "2026-05-10"));
    }

    @Test
    void testMonth() {
        assertTrue(eval("MONTH=5", "2026-05-10"));
        assertFalse(eval("MONTH=4", "2026-05-10"));
    }

    @Test
    void testQuarter() {
        assertTrue(eval("QUARTER=2", "2026-05-10"));
        assertTrue(eval("QUARTER=3", "2026-07-01"));
    }

    @Test
    void testDayOfMonth() {
        assertTrue(eval("DAY_OF_MONTH=10", "2026-05-10"));
        assertFalse(eval("DAY_OF_MONTH=11", "2026-05-10"));
    }

    @Test
    void testDayOfMonthFromEnd() {
        assertTrue(eval("DAY_OF_MONTH_FROM_END=1", "2026-05-31"));
        assertTrue(eval("DAY_OF_MONTH_FROM_END=2", "2026-05-30"));
    }

    @Test
    void testDayOfWeek() {
        // 2026-05-10 是周日（ISO 7）
        assertTrue(eval("DAY_OF_WEEK=7", "2026-05-10"));
        assertFalse(eval("DAY_OF_WEEK=1", "2026-05-10"));
    }

    @Test
    void testWeekOfMonth() {
        assertTrue(eval("WEEK_OF_MONTH=1", "2026-05-01"));
        assertTrue(eval("WEEK_OF_MONTH=2", "2026-05-08"));
        assertTrue(eval("WEEK_OF_MONTH=5", "2026-05-31"));
    }

    @ParameterizedTest
    @CsvSource({
            "2026-01-01, 1",    // Q1 第1天
            "2026-03-31, 90",   // 非闰年Q1最后一天
            "2026-04-01, 1",    // Q2 第1天
            "2026-05-31, 61",   // 回归：5月31日
            "2026-08-31, 62",   // 边界：8月31日
            "2026-12-31, 92",   // 非闰年Q4最后一天
            "2024-02-29, 60",   // 闰年2月29日（Q1第60天）
            "2024-12-31, 92"    // 闰年Q4同样92天
    })
    void testDayOfQuarter(String dateStr, int expectedDay) {
        assertTrue(eval("DAY_OF_QUARTER=" + expectedDay, dateStr));
    }

    @Test
    void testLeapYearFields() {
        // 闰年2月29日是有效的，字段计算应正确
        assertTrue(eval("MONTH=2 && DAY_OF_MONTH=29", "2024-02-29"));
        // 非闰年2月没有29日，但日期解析不会报错，Calendar会自动进位到3月1日，DAY_OF_MONTH会变成1
        // 所以条件会为 false，这里验证条件结果
        assertFalse(eval("MONTH=2 && DAY_OF_MONTH=29", "2023-02-28"));
    }

    @Test
    void testBooleanFields() {
        assertTrue(eval("IS_WEEKDAY", "2026-05-11"));
        assertFalse(eval("IS_WEEKDAY", "2026-05-10"));
        assertTrue(eval("IS_WEEKEND", "2026-05-10"));

        assertTrue(eval("IS_LAST_DAY_OF_MONTH", "2026-05-31"));
        assertFalse(eval("IS_LAST_DAY_OF_MONTH", "2026-05-30"));

        assertTrue(eval("IS_FIRST_WEEK_OF_MONTH", "2026-05-01"));
        assertFalse(eval("IS_FIRST_WEEK_OF_MONTH", "2026-05-08"));

        assertTrue(eval("IS_LAST_WEEK_OF_MONTH", "2026-05-29"));
        assertFalse(eval("IS_LAST_WEEK_OF_MONTH", "2026-05-28"));

        assertTrue(eval("IS_LAST_MONTH_OF_QUARTER", "2026-03-15"));
        assertFalse(eval("IS_LAST_MONTH_OF_QUARTER", "2026-02-15"));
    }

    @Test
    void testBooleanNegation() {
        assertTrue(eval("!IS_WEEKEND", "2026-05-11"));
        assertFalse(eval("!IS_WEEKEND", "2026-05-10"));
    }

    // ==================== 运算符和优先级 ====================

    @Test
    void testAndOr() {
        assertFalse(eval("YEAR=2026 && MONTH=4", "2026-05-10"));
        assertTrue(eval("YEAR=2026 || MONTH=4", "2026-05-10"));
    }

    @Test
    void testNotPrecedence() {
        assertTrue(eval("!MONTH=4 && YEAR=2026", "2026-05-10"));
    }

    @Test
    void testParentheses() {
        assertFalse(eval("(YEAR=2026 || YEAR=2025) && MONTH=4", "2026-05-10"));
        assertTrue(eval("YEAR=2026 || (MONTH=4 && YEAR=2025)", "2026-05-10"));
    }

    @Test
    void testInNotIn() {
        assertTrue(eval("MONTH IN (4,5,6)", "2026-05-10"));
        assertFalse(eval("MONTH IN (1,2,3)", "2026-05-10"));
        assertTrue(eval("MONTH NOT IN (1,2,3)", "2026-05-10"));
        assertFalse(eval("MONTH NOT IN (4,5,6)", "2026-05-10"));
    }

    @Test
    void testComparators() {
        assertTrue(eval("DAY_OF_MONTH > 1", "2026-05-10"));
        assertTrue(eval("DAY_OF_MONTH >= 10", "2026-05-10"));
        assertTrue(eval("DAY_OF_MONTH < 11", "2026-05-10"));
        assertTrue(eval("DAY_OF_MONTH <= 10", "2026-05-10"));
        assertTrue(eval("DAY_OF_MONTH != 9", "2026-05-10"));
        assertTrue(eval("DAY_OF_MONTH == 10", "2026-05-10"));
    }

    // ==================== 增强边界测试 ====================

    @Test
    void testCaseInsensitivity() {
        // 小写字段名应能正确识别
        assertTrue(eval("day_of_month=10", "2026-05-10"));
        assertTrue(eval("Is_Weekday", "2026-05-11"));
        assertTrue(eval("MONTH IN (4,5,6)", "2026-05-10"));
        // 混合大小写
        assertTrue(eval("Day_Of_Week=7", "2026-05-10"));
    }

    @Test
    void testDoubleNegation() {
        // !!IS_WEEKDAY 等于 IS_WEEKDAY
        assertTrue(eval("!!IS_WEEKDAY", "2026-05-11"));
        assertFalse(eval("!!IS_WEEKDAY", "2026-05-10"));
        // 三重否定等效于单次否定
        assertFalse(eval("!!!IS_WEEKDAY", "2026-05-11"));
        assertTrue(eval("!!!IS_WEEKDAY", "2026-05-10"));
    }

    @Test
    void testNegativeNumberRejected() {
        // parseNumber 现在会拒绝负数，应抛出异常
        assertThrows(ConditionEvaluator.ConditionEvaluationException.class,
                () -> eval("DAY_OF_MONTH=-1", "2026-05-10"));
        assertThrows(ConditionEvaluator.ConditionEvaluationException.class,
                () -> eval("MONTH IN (-1, 2)", "2026-05-10"));
    }

    @Test
    void testDeeplyNestedExpression() {
        // 复杂嵌套，混合 IN、比较符、布尔字段、括号
        String expr = "(YEAR=2026 || YEAR=2027) && (MONTH IN (1,6) || !IS_WEEKEND) && (DAY_OF_MONTH >= 15)";
        // 2026-06-17 是周五（weekday），MONTH=6在IN中，DAY=17>=15，YEAR=2026 => true
        assertTrue(eval(expr, "2026-06-17"));
        // 2026-02-14 是周六（weekend），MONTH=2不在IN，但!IS_WEEKEND为false，所以整体false
        assertFalse(eval(expr, "2026-02-14"));
        // 2027-01-15 是周五，YEAR=2027，MONTH=1在IN中，IS_WEEKDAY => 满足 => true
        assertTrue(eval(expr, "2027-01-15"));
    }

    @Test
    void testExpressionWithSpaces() {
        // 多余空格不应影响解析
        assertTrue(eval("  YEAR   = 2026   &&   MONTH = 5 ", "2026-05-10"));
        assertTrue(eval("  (  MONTH  IN  ( 5 , 6 )  )  ", "2026-05-10"));
    }

    // ==================== 异常路径 ====================

    @Test
    void testBooleanWithComparatorShouldThrow() {
        assertThrows(ConditionEvaluator.ConditionEvaluationException.class,
                () -> eval("IS_WEEKDAY = 1", "2026-05-10"));
    }

    @Test
    void testNumericWithoutComparatorShouldThrow() {
        assertThrows(ConditionEvaluator.ConditionEvaluationException.class,
                () -> eval("YEAR", "2026-05-10"));
    }

    @Test
    void testUnknownFieldShouldThrow() {
        assertThrows(ConditionEvaluator.ConditionEvaluationException.class,
                () -> eval("FOO=1", "2026-05-10"));
    }

    @Test
    void testTrailingGarbageShouldThrow() {
        assertThrows(ConditionEvaluator.ConditionEvaluationException.class,
                () -> eval("YEAR=2026 )", "2026-05-10"));
    }

    @Test
    void testMalformedExpressionShouldThrow() {
        assertThrows(ConditionEvaluator.ConditionEvaluationException.class,
                () -> eval("YEAR = = 2026", "2026-05-10"));
    }

    // ==================== 空值处理 ====================

    @Test
    void testNullAndEmpty() {
        assertTrue(ConditionEvaluator.evaluate(null));
        assertTrue(ConditionEvaluator.evaluate(""));
        assertTrue(ConditionEvaluator.evaluate("   "));
        assertTrue(ConditionEvaluator.evaluate(null, new Date()));
        assertTrue(ConditionEvaluator.evaluate("", new Date()));
    }

    // 放在异常路径或边界测试区域

    @Test
    void testReferenceDateNull() {
        assertThrows(NullPointerException.class,
                () -> ConditionEvaluator.evaluate("YEAR=2026", null));
    }

    @Test
    void testFieldValueOutOfRange() {
        // 月份超出 1-12
        assertFalse(eval("MONTH=13", "2026-05-10"));
        assertTrue(eval("MONTH != 13", "2026-05-10"));
        // 日期超出 1-31
        assertFalse(eval("DAY_OF_MONTH=32", "2026-05-10"));
        // 星期超出 1-7
        assertFalse(eval("DAY_OF_WEEK=8", "2026-05-10"));
        // 季度超出 1-4
        assertFalse(eval("QUARTER=5", "2026-05-10"));
    }

    // ==================== 辅助方法 ====================

    private boolean eval(String expr, String dateStr) {
        return ConditionEvaluator.evaluate(expr, date(dateStr));
    }
}