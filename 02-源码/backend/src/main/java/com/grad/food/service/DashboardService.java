package com.grad.food.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.grad.food.common.Status;
import com.grad.food.entity.*;
import com.grad.food.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 食品安全看板。
 * 统计一律后端聚合，前端不做全表二次计算；图表点击后带的筛选条件与这里口径一致，
 * 保证「统计值 = 明细总数」。
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final FoodSampleMapper sampleMapper;
    private final SampleTaskMapper taskMapper;
    private final InspectionRecordMapper inspectMapper;
    private final FoodIncidentMapper incidentMapper;
    private final DisposalRecordMapper disposalMapper;
    private final WorkshopMapper workshopMapper;
    private final FoodCategoryMapper categoryMapper;
    private final EvalService evalService;

    public Map<String, Object> overview(String range) {
        LocalDate today = LocalDate.now();
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = today.atTime(23, 59, 59);

        // ---------- 6 个核心指标 ----------
        long shouldToday = taskMapper.selectCount(new LambdaQueryWrapper<SampleTask>()
                .ge(SampleTask::getCreateTime, dayStart).le(SampleTask::getCreateTime, dayEnd));
        long actualToday = sampleMapper.selectCount(new LambdaQueryWrapper<FoodSample>()
                .ge(FoodSample::getStoreTime, dayStart).le(FoodSample::getStoreTime, dayEnd)
                .ne(FoodSample::getStatus, Status.REJECTED));
        double actualRate = shouldToday == 0 ? 0 : Math.round(actualToday * 1000.0 / shouldToday) / 10.0;

        List<FoodSample> recent = sampleMapper.selectList(new LambdaQueryWrapper<FoodSample>()
                .in(FoodSample::getStatus, Status.RETAINING, Status.PENDING_DISPOSE, Status.DISPOSED)
                .ge(FoodSample::getStoreTime, today.minusDays(30).atStartOfDay()));
        long weightOk = recent.stream().filter(s -> s.getWeight() != null
                && s.getWeight().compareTo(BigDecimal.valueOf(125)) >= 0).count();
        double weightRate = recent.isEmpty() ? 0 : Math.round(weightOk * 1000.0 / recent.size()) / 10.0;

        long openIncidents = incidentMapper.selectCount(new LambdaQueryWrapper<FoodIncident>()
                .ne(FoodIncident::getStatus, Status.INC_CLOSED));
        long overdue = sampleMapper.selectCount(new LambdaQueryWrapper<FoodSample>()
                .eq(FoodSample::getStatus, Status.PENDING_DISPOSE)
                .lt(FoodSample::getExpireTime, LocalDateTime.now()));
        long monthTasks = taskMapper.selectCount(new LambdaQueryWrapper<SampleTask>()
                .ge(SampleTask::getCreateTime, today.withDayOfMonth(1).atStartOfDay()));
        long monthDone = taskMapper.selectCount(new LambdaQueryWrapper<SampleTask>()
                .ge(SampleTask::getCreateTime, today.withDayOfMonth(1).atStartOfDay())
                .in(SampleTask::getStatus, Status.RETAINING, Status.PENDING_DISPOSE, Status.DISPOSED));
        double ownerRate = monthTasks == 0 ? 0 : Math.round(monthDone * 1000.0 / monthTasks) / 10.0;

        List<Map<String, Object>> kpis = new ArrayList<>();
        kpis.add(kpi("应留样数（今日）", shouldToday, "份", "", ""));
        kpis.add(kpi("实际留样率", actualRate, "%", actualToday + " / " + shouldToday + " 份已入柜", "teal"));
        kpis.add(kpi("重量合格率", weightRate, "%", weightOk + " / " + recent.size() + " 份达标", "ok"));
        kpis.add(kpi("检测异常数", openIncidents, "件", "未关闭事件", "bad"));
        kpis.add(kpi("超期未处置", overdue, "份", "超过保留时限仍未销毁", "warn"));
        kpis.add(kpi("责任人完成率", ownerRate, "%", monthDone + " / " + monthTasks + " 项任务", "neutral"));

        // ---------- 图表 1：近 7 日应留 / 实留 ----------
        List<String> days = new ArrayList<>();
        List<Long> shouldSeries = new ArrayList<>();
        List<Long> actualSeries = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            days.add(d.toString().substring(5));
            shouldSeries.add(taskMapper.selectCount(new LambdaQueryWrapper<SampleTask>()
                    .ge(SampleTask::getCreateTime, d.atStartOfDay())
                    .le(SampleTask::getCreateTime, d.atTime(23, 59, 59))));
            actualSeries.add(sampleMapper.selectCount(new LambdaQueryWrapper<FoodSample>()
                    .ge(FoodSample::getStoreTime, d.atStartOfDay())
                    .le(FoodSample::getStoreTime, d.atTime(23, 59, 59))
                    .ne(FoodSample::getStatus, Status.REJECTED)));
        }

        // ---------- 图表 2：留样状态分布 ----------
        List<Map<String, Object>> statusDist = new ArrayList<>();
        for (String s : List.of(Status.PENDING_REVIEW, Status.RETAINING, Status.PENDING_DISPOSE,
                Status.DISPOSED, Status.REJECTED)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", Status.sampleLabel(s));
            m.put("code", s);
            m.put("value", sampleMapper.selectCount(new LambdaQueryWrapper<FoodSample>().eq(FoodSample::getStatus, s)));
            statusDist.add(m);
        }

        // ---------- 图表 3：档口留样完成率 ----------
        List<Map<String, Object>> workshopRate = new ArrayList<>();
        for (Workshop w : workshopMapper.selectList(null)) {
            long total = taskMapper.selectCount(new LambdaQueryWrapper<SampleTask>()
                    .likeRight(SampleTask::getBatchNo, "PC-").ge(SampleTask::getCreateTime, today.minusDays(30).atStartOfDay()));
            // 档口完成率：该档口批次下任务完成占比（无任务时按 0 处理，避免除零）
            long done = taskMapper.selectCount(new LambdaQueryWrapper<SampleTask>()
                    .ge(SampleTask::getCreateTime, today.minusDays(30).atStartOfDay())
                    .in(SampleTask::getStatus, Status.RETAINING, Status.PENDING_DISPOSE, Status.DISPOSED)
                    .inSql(SampleTask::getBatchId, "select id from food_batch where workshop_id = " + w.getId()));
            long all = taskMapper.selectCount(new LambdaQueryWrapper<SampleTask>()
                    .ge(SampleTask::getCreateTime, today.minusDays(30).atStartOfDay())
                    .inSql(SampleTask::getBatchId, "select id from food_batch where workshop_id = " + w.getId()));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", w.getName());
            m.put("total", all);
            m.put("done", done);
            m.put("rate", all == 0 ? 0 : Math.round(done * 1000.0 / all) / 10.0);
            workshopRate.add(m);
        }
        workshopRate.sort((a, b) -> Double.compare((double) b.get("rate"), (double) a.get("rate")));

        // ---------- 图表 4：重量不合格率趋势（近 5 个月） ----------
        List<String> months = new ArrayList<>();
        List<Double> weightFailSeries = new ArrayList<>();
        for (int i = 4; i >= 0; i--) {
            LocalDate m0 = today.minusMonths(i).withDayOfMonth(1);
            LocalDate m1 = m0.plusMonths(1).minusDays(1);
            months.add(m0.getMonthValue() + " 月");
            List<FoodSample> list = sampleMapper.selectList(new LambdaQueryWrapper<FoodSample>()
                    .ge(FoodSample::getStoreTime, m0.atStartOfDay())
                    .le(FoodSample::getStoreTime, m1.atTime(23, 59, 59)));
            long bad = list.stream().filter(s -> s.getWeight() != null
                    && s.getWeight().compareTo(BigDecimal.valueOf(125)) < 0).count();
            weightFailSeries.add(list.isEmpty() ? 0.0 : Math.round(bad * 1000.0 / list.size()) / 10.0);
        }

        // ---------- 图表 5：检测结果分布 ----------
        List<Map<String, Object>> inspectDist = new ArrayList<>();
        for (String s : List.of(Status.INS_PASSED, Status.INS_RECHECK, Status.INS_FAILED, Status.INS_PENDING)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", Status.inspectLabel(s));
            m.put("value", inspectMapper.selectCount(new LambdaQueryWrapper<InspectionRecord>()
                    .eq(InspectionRecord::getStatus, s)));
            inspectDist.add(m);
        }

        // ---------- 图表 6：不合格事件趋势（近 6 个月） ----------
        List<String> incMonths = new ArrayList<>();
        List<Long> incSeries = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            LocalDate m0 = today.minusMonths(i).withDayOfMonth(1);
            LocalDate m1 = m0.plusMonths(1).minusDays(1);
            incMonths.add(m0.getMonthValue() + "月");
            incSeries.add(incidentMapper.selectCount(new LambdaQueryWrapper<FoodIncident>()
                    .ge(FoodIncident::getOccurTime, m0.atStartOfDay())
                    .le(FoodIncident::getOccurTime, m1.atTime(23, 59, 59))));
        }

        // ---------- 图表 7：创新点结果分布与采纳率 ----------
        Map<String, Object> evalStats = evalService.stats();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("kpis", kpis);
        data.put("trend", Map.of("days", days, "should", shouldSeries, "actual", actualSeries));
        data.put("statusDist", statusDist);
        data.put("workshopRate", workshopRate);
        data.put("weightFail", Map.of("months", months, "values", weightFailSeries));
        data.put("inspectDist", inspectDist);
        data.put("incidentTrend", Map.of("months", incMonths, "values", incSeries));
        data.put("evalStats", evalStats);
        data.put("range", range == null ? "30d" : range);
        data.put("updated", LocalDateTime.now().withNano(0));
        return data;
    }

    private Map<String, Object> kpi(String label, Number value, String unit, String desc, String tone) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("value", value);
        m.put("unit", unit);
        m.put("desc", desc);
        m.put("tone", tone);
        return m;
    }
}
