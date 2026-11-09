package com.ts.core.scheduler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CronExpression 测试套件。
 *
 * <p>字段约定（与实现一致）：
 * second(0-59) minute(0-59) hour(0-23) day-of-month(1-31) month(1-12) day-of-week(0-7，0/7=周日，1=周一...6=周六)。
 *
 * <p>不支持字母别名（MON/FRI/JAN 等）——parseField 对非 *、非 ?、非数字的 token
 * 一律走 Integer.parseInt 失败分支，因此测试全部使用数值。
 *
 * <p>唯一例外是 day-of-month 的 {@code L} / {@code L-N}（见第 10 节）：{@code L}
 * 表示当月最后一天，{@code L-N} 表示 {@code 当月最后一天 - N}。偏移在匹配时才
 * 解析，因此同一个偏移在不同月份落到不同日期；短月里够不到第一天的偏移自动不匹配，
 * 不会报错。
 *
 * <p>day-of-month 与 day-of-week 必须严格满足"有且仅有一个为 ?"（Quartz 语义），
 * 这是本次修复的核心校验，专门用一组测试覆盖。
 *
 * <p>参考基准日期：2026-08-09 是周日，2026-08-10 是周一，……，2026-08-15 是周六，
 * 2026-08-16 是下一个周日。
 */
class CronExpressionTest {

    // ==================== 1. 基本解析 ====================

    @Test
    void shouldParseSimpleExpression() throws ParseException {
        CronExpression cron = new CronExpression("0 0 12 1 1 ?");
        assertNotNull(cron.getNextValidTimeAfter(new Date()));
    }

    @Test
    void validateExpressionShouldNotThrowForValid() {
        assertDoesNotThrow(() -> CronExpression.validateExpression("0 0 12 ? * 1-5"));
    }

    @Test
    void toStringShouldReturnOriginalExpression() throws ParseException {
        String expr = "0 0 12 ? * 1-5";
        CronExpression cron = new CronExpression(expr);
        assertEquals(expr, cron.toString());
    }

    // ==================== 2. ?/* 严格区分与 Quartz 校验（核心修复） ====================

    @Test
    void shouldRejectWhenNeitherDomNorDowIsQuestionMark() {
        // 两者都给具体值
        assertThrows(ParseException.class, () -> new CronExpression("0 0 12 1 1 1"));
        // 两者都是 '*'（'*' 属于"非 ?"，同样违反"有且仅有一个 ?"的约束）
        assertThrows(ParseException.class, () -> new CronExpression("0 0 12 * * *"));
        // 一个具体值 + 一个 '*'，同样都不是 '?'
        assertThrows(ParseException.class, () -> new CronExpression("0 0 12 15 * 1-5"));
    }

    @Test
    void shouldRejectWhenBothDomAndDowAreQuestionMark() {
        assertThrows(ParseException.class, () -> new CronExpression("0 0 12 ? * ?"));
    }

    @Test
    void shouldAcceptDayOfMonthQuestionMark() throws ParseException {
        assertNotNull(new CronExpression("0 0 12 ? * 1-5"));
    }

    @Test
    void shouldAcceptDayOfWeekQuestionMark() throws ParseException {
        assertNotNull(new CronExpression("0 0 12 1 * ?"));
    }

    @Test
    void domQuestionMarkShouldDeferEntirelyToDayOfWeek() throws ParseException {
        // DOM='?'：只看星期，周一到周五才触发
        CronExpression cron = new CronExpression("0 0 12 ? * 1-5");

        Date sunday = date("2026-08-09 00:00:00");
        // 周日之后，下一个工作日是周一
        assertEquals(date("2026-08-10 12:00:00"), cron.getNextValidTimeAfter(sunday));

        Date friday = date("2026-08-14 13:00:00");
        // 周五13点之后，工作日范围内下一次是下周一（周六周日被跳过）
        assertEquals(date("2026-08-17 12:00:00"), cron.getNextValidTimeAfter(friday));
    }

    @Test
    void dowQuestionMarkShouldDeferEntirelyToDayOfMonth() throws ParseException {
        // DOW='?'：只看日期，不管星期几，'*' 表示每天都符合日期条件
        CronExpression everyDay = new CronExpression("0 0 12 * * ?");
        Date sunday = date("2026-08-09 00:00:00");
        // 星期字段被完全忽略，当天（周日）就应该触发
        assertEquals(date("2026-08-09 12:00:00"), everyDay.getNextValidTimeAfter(sunday));

        // 具体某一天 + DOW='?'：不管那天是星期几都应该触发
        CronExpression onThe15th = new CronExpression("0 0 12 15 * ?");
        Date aug1 = date("2026-08-01 00:00:00");
        assertEquals(date("2026-08-15 12:00:00"), onThe15th.getNextValidTimeAfter(aug1));
    }

    // ==================== 3. 周日 0/7 归一化 ====================

    @Test
    void zeroAndSevenShouldBothMeanSunday() throws ParseException {
        CronExpression sunday0 = new CronExpression("0 0 12 ? * 0");
        CronExpression sunday7 = new CronExpression("0 0 12 ? * 7");

        Date saturday = date("2026-08-08 00:00:00");
        Date expected = date("2026-08-09 12:00:00");

        assertEquals(expected, sunday0.getNextValidTimeAfter(saturday));
        assertEquals(expected, sunday7.getNextValidTimeAfter(saturday));
    }

    @Test
    void zeroAndSevenShouldBeEquivalentInsideList() throws ParseException {
        // "0,3" 与 "3,7" 应该产生完全相同的匹配结果（周日+周三）
        CronExpression withZero = new CronExpression("0 0 12 ? * 0,3");
        CronExpression withSeven = new CronExpression("0 0 12 ? * 3,7");

        Date saturday = date("2026-08-08 00:00:00");
        Date expectedSunday = date("2026-08-09 12:00:00");

        assertEquals(expectedSunday, withZero.getNextValidTimeAfter(saturday));
        assertEquals(expectedSunday, withSeven.getNextValidTimeAfter(saturday));
    }

    @Test
    void rangeCrossingSundayShouldIncludeBothZeroAndSevenSemantics() throws ParseException {
        // "5-7" 表示周五、周六、周日
        CronExpression fridayToSunday = new CronExpression("0 0 12 ? * 5-7");

        Date monday = date("2026-08-10 00:00:00");
        // 周一之后，下一个匹配是本周五
        assertEquals(date("2026-08-14 12:00:00"), fridayToSunday.getNextValidTimeAfter(monday));
    }

    @Test
    void sundayOnlyExpressionShouldSkipWeekdays() throws ParseException {
        CronExpression sundayOnly = new CronExpression("0 0 12 ? * 7");

        Date sundayMidnight = date("2026-08-09 00:00:00");
        assertEquals(date("2026-08-09 12:00:00"), sundayOnly.getNextValidTimeAfter(sundayMidnight));

        Date monday = date("2026-08-10 00:00:00");
        assertEquals(date("2026-08-16 12:00:00"), sundayOnly.getNextValidTimeAfter(monday));
    }

    // ==================== 4. 边界值 ====================

    @ParameterizedTest
    @ValueSource(strings = {
            "0 0 0 1 1 ?",          // 最小小时/日/月
            "59 59 23 31 12 ?",     // 最大秒/分/时/日/月
            "0 0 12 ? * 0",         // 星期最小值（周日）
            "0 0 12 ? * 7",         // 星期最大值（周日，另一种写法）
            "0 0 12 1-31 * ?",      // 日字段全范围
            "0 0 12 ? * 0,1,2,3,4,5,6,7"
    })
    void shouldParseBoundaryValues(String expression) {
        assertDoesNotThrow(() -> new CronExpression(expression));
    }

    @Test
    void shouldRejectOutOfRangeValues() {
        assertThrows(ParseException.class, () -> new CronExpression("60 0 12 ? * 1"));  // 秒 > 59
        assertThrows(ParseException.class, () -> new CronExpression("0 60 12 ? * 1"));  // 分 > 59
        assertThrows(ParseException.class, () -> new CronExpression("0 0 24 ? * 1"));   // 时 > 23
        assertThrows(ParseException.class, () -> new CronExpression("0 0 12 32 * ?"));  // 日 > 31
        assertThrows(ParseException.class, () -> new CronExpression("0 0 12 0 * ?"));   // 日 < 1
        assertThrows(ParseException.class, () -> new CronExpression("0 0 12 ? 13 1"));  // 月 > 12
        assertThrows(ParseException.class, () -> new CronExpression("0 0 12 ? 0 1"));   // 月 < 1
        assertThrows(ParseException.class, () -> new CronExpression("0 0 12 ? * 8"));   // 周 > 7
    }

    @Test
    void shouldRejectNegativeValues() {
        assertThrows(ParseException.class, () -> new CronExpression("-1 0 12 ? * 1"));
        assertThrows(ParseException.class, () -> new CronExpression("0 0 12 -5 * ?"));
    }

    @Test
    void shouldRejectInvalidRange() {
        // start > end
        assertThrows(ParseException.class, () -> new CronExpression("0 0 12 20-10 * ?"));
        // 格式不完整
        assertThrows(ParseException.class, () -> new CronExpression("0 0 12 10- * ?"));
    }

    // ==================== 5. 列表去重与排序 ====================

    @Test
    void listShouldDeduplicateAndSort() throws ParseException {
        // 分钟字段 "5,3,5,1" 应被去重排序为 {1,3,5}
        CronExpression cron = new CronExpression("0 5,3,5,1 10 1 * ?");

        Date beforeFirstMatch = date("2026-08-01 10:00:30");
        assertEquals(date("2026-08-01 10:01:00"), cron.getNextValidTimeAfter(beforeFirstMatch));

        Date afterFirstMatch = date("2026-08-01 10:01:30");
        assertEquals(date("2026-08-01 10:03:00"), cron.getNextValidTimeAfter(afterFirstMatch));
    }

    // ==================== 6. 步长语法 ====================

    @Test
    void shouldSupportWildcardStep() throws ParseException {
        // 秒字段 "5/10" → {5,15,25,35,45,55}
        CronExpression cron = new CronExpression("5/10 0 0 * * ?");

        Date midnight = date("2026-08-09 00:00:00");
        assertEquals(date("2026-08-09 00:00:05"), cron.getNextValidTimeAfter(midnight));
    }

    @Test
    void shouldSupportStepWithExplicitStart() throws ParseException {
        // 小时字段 "1/6" → {1,7,13,19}
        CronExpression cron = new CronExpression("0 0 1/6 * * ?");

        Date earlyMorning = date("2026-08-09 02:00:00");
        assertEquals(date("2026-08-09 07:00:00"), cron.getNextValidTimeAfter(earlyMorning));
    }

    @Test
    void shouldRejectStepOnRange() {
        // 暂不支持 "1-10/2" 这种 range+step 组合语法
        assertThrows(ParseException.class, () -> new CronExpression("0 0 12 1-10/2 * ?"));
    }

    @Test
    void shouldRejectNonPositiveStep() {
        assertThrows(ParseException.class, () -> new CronExpression("*/0 0 12 ? * 1"));
        assertThrows(ParseException.class, () -> new CronExpression("*/-5 0 12 ? * 1"));
    }

    // ==================== 7. 运行时调度计算 ====================

    @Test
    void shouldReturnCorrectNextTimeAcrossWeekBoundary() throws ParseException {
        CronExpression cron = new CronExpression("0 30 9 ? * 1-5");

        Date sunday = date("2026-08-09 10:00:00");
        assertEquals(date("2026-08-10 09:30:00"), cron.getNextValidTimeAfter(sunday));
    }

    @Test
    void shouldAlwaysReturnStrictlyFutureTime() throws ParseException {
        // 传入的时间点本身恰好就是一个合法触发时刻，
        // 结果必须是"下一次"，而不是同一时刻
        CronExpression cron = new CronExpression("0 0 12 * * ?");

        Date exactTrigger = date("2026-08-09 12:00:00");
        assertEquals(date("2026-08-10 12:00:00"), cron.getNextValidTimeAfter(exactTrigger));
    }

    @Test
    void shouldCrossMonthBoundary() throws ParseException {
        CronExpression cron = new CronExpression("0 0 12 1 * ?");

        Date lateAugust = date("2026-08-20 00:00:00");
        assertEquals(date("2026-09-01 12:00:00"), cron.getNextValidTimeAfter(lateAugust));
    }

    @Test
    void shouldCrossYearBoundary() throws ParseException {
        CronExpression cron = new CronExpression("0 0 12 1 1 ?");

        Date lateDecember = date("2026-12-20 00:00:00");
        assertEquals(date("2027-01-01 12:00:00"), cron.getNextValidTimeAfter(lateDecember));
    }

    // ==================== 8. 闰年与无解终止 ====================

    @Test
    void shouldHandleLeapYearFeb29() throws ParseException {
        CronExpression cron = new CronExpression("0 0 12 29 2 ?");

        Date beforeLeap = date("2024-02-28 23:00:00");
        assertEquals(date("2024-02-29 12:00:00"), cron.getNextValidTimeAfter(beforeLeap));

        // 从非闰年 2025 出发，应跳到下一个闰年 2028
        Date nonLeapYear = date("2025-03-01 00:00:00");
        assertEquals(date("2028-02-29 12:00:00"), cron.getNextValidTimeAfter(nonLeapYear));
    }

    @Test
    void shouldReturnNullWhenNoFutureMatchWithinIterationLimit() throws ParseException {
        // 2 月 30 日永远不存在，算法应在迭代上限内终止并返回 null，而不是死循环
        CronExpression cron = new CronExpression("0 0 12 30 2 ?");

        Date farFuture = date("2099-01-01 00:00:00");
        assertNull(cron.getNextValidTimeAfter(farFuture));
    }

    // ==================== 9. 非法输入 ====================

    @Test
    void shouldThrowOnInvalidFieldCount() {
        assertThrows(ParseException.class, () -> new CronExpression("0 0 12 ? *"));            // 5 个字段
        assertThrows(ParseException.class, () -> new CronExpression("0 0 12 ? * 1 2026"));      // 7 个字段
        assertThrows(ParseException.class, () -> new CronExpression(""));                        // 空字符串
    }

    @Test
    void shouldRejectAlphabeticAliasesSinceUnsupported() {
        // 当前实现不支持 MON/FRI/JAN 等别名，字母 token 一律按非法数字处理
        assertThrows(ParseException.class, () -> new CronExpression("0 0 12 ? * MON"));
        assertThrows(ParseException.class, () -> new CronExpression("0 0 12 ? * MON-FRI"));
        assertThrows(ParseException.class, () -> new CronExpression("0 0 12 1 JAN ?"));
        assertThrows(ParseException.class, () -> new CronExpression("0 0 12 ? * ABC"));
    }

    @Test
    void shouldRejectMalformedTokens() {
        assertThrows(ParseException.class, () -> new CronExpression("a 0 12 ? * 1"));
        assertThrows(ParseException.class, () -> new CronExpression("0 0 12 ? * 1,,2"));
        assertThrows(ParseException.class, () -> new CronExpression("0 0 12 ? * 1-2-3"));
    }

    // ==================== 10. L / L-N 月末偏移 ====================

    @Test
    void shouldMatchLastDayOfMonthWithL() throws ParseException {
        CronExpression cron = new CronExpression("0 0 2 L * ?");

        // 2026-09 是 30 天月
        assertEquals(date("2026-09-30 02:00:00"), cron.getNextValidTimeAfter(date("2026-09-15 00:00:00")));
        // 2026-01 是 31 天月
        assertEquals(date("2026-01-31 02:00:00"), cron.getNextValidTimeAfter(date("2026-01-01 00:00:00")));
    }

    @Test
    void lShouldResolveAgainstEachMonthItCrosses() throws ParseException {
        CronExpression cron = new CronExpression("0 0 2 L * ?");

        // 从当月最后一天的触发时刻出发，下一次必须是「下一个月的最后一天」，
        // 而不是把 30 号这个数字带到 31 天月里
        assertEquals(date("2026-10-31 02:00:00"), cron.getNextValidTimeAfter(date("2026-09-30 02:00:00")));
        assertEquals(date("2026-11-30 02:00:00"), cron.getNextValidTimeAfter(date("2026-10-31 02:00:00")));
    }

    @Test
    void lMinusFourShouldBeFifthToLastDay() throws ParseException {
        CronExpression cron = new CronExpression("0 0 2 L-4 * ?");

        // 9 月 30 天，最后一天往前 4 天 = 26 号，即倒数第 5 天
        assertEquals(date("2026-09-26 02:00:00"), cron.getNextValidTimeAfter(date("2026-09-01 00:00:00")));
        // 1 月 31 天 → 27 号
        assertEquals(date("2026-01-27 02:00:00"), cron.getNextValidTimeAfter(date("2026-01-01 00:00:00")));
    }

    @Test
    void lMinusZeroShouldBehaveLikeL() throws ParseException {
        CronExpression cron = new CronExpression("0 0 2 L-0 * ?");
        assertEquals(date("2026-09-30 02:00:00"), cron.getNextValidTimeAfter(date("2026-09-15 00:00:00")));
    }

    @Test
    void lShouldFollowFebruaryAcrossLeapAndNonLeapYears() throws ParseException {
        CronExpression cron = new CronExpression("0 0 2 L 2 ?");

        assertEquals(date("2024-02-29 02:00:00"), cron.getNextValidTimeAfter(date("2024-01-01 00:00:00")));
        assertEquals(date("2025-02-28 02:00:00"), cron.getNextValidTimeAfter(date("2025-01-01 00:00:00")));
    }

    @Test
    void offsetBeyondShortMonthShouldSkipThatMonthInsteadOfFailing() throws ParseException {
        // L-29 在 31 天月 = 2 号，30 天月 = 1 号，2 月（28/29 天）够不到 → 不匹配
        CronExpression cron = new CronExpression("0 0 2 L-29 * ?");

        assertEquals(date("2026-01-02 02:00:00"), cron.getNextValidTimeAfter(date("2026-01-01 00:00:00")));
        // 1 月 2 号之后应直接跳到 3 月，跳过整个 2 月
        assertEquals(date("2026-03-02 02:00:00"), cron.getNextValidTimeAfter(date("2026-01-02 02:00:00")));
        // 3 月之后是 4 月 1 号（30 天月）
        assertEquals(date("2026-04-01 02:00:00"), cron.getNextValidTimeAfter(date("2026-03-02 02:00:00")));
    }

    @Test
    void lShouldCombineWithOtherValuesAsUnion() throws ParseException {
        CronExpression cron = new CronExpression("0 0 2 L-4,15 * ?");

        // 先命中 15 号，再命中倒数第 5 天（9 月 → 26 号）
        assertEquals(date("2026-09-15 02:00:00"), cron.getNextValidTimeAfter(date("2026-09-01 00:00:00")));
        assertEquals(date("2026-09-26 02:00:00"), cron.getNextValidTimeAfter(date("2026-09-15 02:00:00")));
        assertEquals(date("2026-10-15 02:00:00"), cron.getNextValidTimeAfter(date("2026-09-26 02:00:00")));
    }

    @Test
    void lWithQuestionMarkDayOfWeekShouldBeAccepted() {
        assertDoesNotThrow(() -> CronExpression.validateExpression("0 0 2 L * ?"));
        assertDoesNotThrow(() -> CronExpression.validateExpression("0 0 2 L-4 1,3 ?"));
        assertDoesNotThrow(() -> CronExpression.validateExpression("0 0 2 L-4,15 * ?"));
    }

    @Test
    void lShouldStillRequireExactlyOneQuestionMark() {
        // 'L' 不是 '?'，因此 day-of-week 必须显式给 '?'
        assertThrows(ParseException.class, () -> new CronExpression("0 0 2 L * *"));
        assertThrows(ParseException.class, () -> new CronExpression("0 0 2 L * 1"));
    }

    @Test
    void lShouldBeRejectedOutsideDayOfMonthField() {
        assertThrows(ParseException.class, () -> new CronExpression("L 0 12 ? * 1"));   // 秒
        assertThrows(ParseException.class, () -> new CronExpression("0 L 12 ? * 1"));   // 分
        assertThrows(ParseException.class, () -> new CronExpression("0 0 12 ? L ?"));   // 月
        assertThrows(ParseException.class, () -> new CronExpression("0 0 12 ? * L"));   // 周
    }

    @Test
    void shouldRejectMalformedLastDayTokens() {
        assertThrows(ParseException.class, () -> new CronExpression("0 0 12 L/2 * ?"));    // 不支持步长
        assertThrows(ParseException.class, () -> new CronExpression("0 0 12 L-4-5 * ?"));  // 不支持双重偏移
        assertThrows(ParseException.class, () -> new CronExpression("0 0 12 L-x * ?"));    // 偏移非数字
        assertThrows(ParseException.class, () -> new CronExpression("0 0 12 LX * ?"));     // 非法后缀
        assertThrows(ParseException.class, () -> new CronExpression("0 0 12 1L * ?"));     // L 不在开头
        assertThrows(ParseException.class, () -> new CronExpression("0 0 12 L-31 * ?"));   // 任何月都够不到
        assertThrows(ParseException.class, () -> new CronExpression("0 0 12 L--1 * ?"));   // 负偏移
    }

    // ==================== 辅助方法 ====================

    private Date date(String yyyyMMdd_HHmmss) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(yyyyMMdd_HHmmss);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}