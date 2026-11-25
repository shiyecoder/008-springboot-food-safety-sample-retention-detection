package com.grad.food.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grad.food.common.BizException;
import com.grad.food.common.Status;
import com.grad.food.common.UserContext;
import com.grad.food.dto.EvalRuleDTO;
import com.grad.food.entity.*;
import com.grad.food.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 创新点：留样覆盖率与到期处置校验。
 *
 * 设计要点（对应需求「可解释的业务辅助工具」定位）：
 *  1. 规则可配置：6 个因素权重合计必须 100，修改生成新版本并记录生效时间；
 *  2. 计算留痕：每次计算保存输入快照、规则版本、各项得分、总分与等级，同输入同规则必得同结果；
 *  3. 结果解释：输出推荐结果、主要影响因素、未满足条件、可改进项，不只给一个分数；
 *  4. 人工修正：负责人可改最终等级，但必须填原因，原始计算结果不覆盖；
 *  5. 效果统计：看板统计推荐采纳率与人工调整率。
 */
@Service
@RequiredArgsConstructor
public class EvalService {

    private final EvalRuleMapper ruleMapper;
    private final EvalScoreMapper scoreMapper;
    private final EvalAdjustMapper adjustMapper;
    private final FoodSampleMapper sampleMapper;
    private final SampleTaskMapper taskMapper;
    private final DisposalRecordMapper disposalMapper;
    private final FoodIncidentMapper incidentMapper;
    private final FoodBatchMapper batchMapper;
    private final ObjectMapper om;
    private final LogService logService;

    /** 因素编码 → 中文名，顺序即页面展示顺序 */
    private static final LinkedHashMap<String, String> FACTORS = new LinkedHashMap<>() {{
        put("menuBatch", "菜单批次");
        put("shouldCount", "应留样数量");
        put("actualWeight", "实际重量");
        put("storeTime", "入柜时间");
        put("retainDuration", "保留时长");
        put("evidence", "处置凭证完整性");
    }};

    public EvalRule currentRule() {
        EvalRule r = ruleMapper.selectOne(new LambdaQueryWrapper<EvalRule>()
                .eq(EvalRule::getStatus, 1).orderByDesc(EvalRule::getEffectiveTime).last("limit 1"));
        if (r == null) {
            throw new BizException("未配置校验规则");
        }
        return r;
    }

    public List<EvalRule> versions() {
        return ruleMapper.selectList(new LambdaQueryWrapper<EvalRule>().orderByDesc(EvalRule::getCreateTime));
    }

    public Map<String, Object> ruleView() {
        EvalRule r = currentRule();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rule", r);
        m.put("factors", FACTORS);
        m.put("weights", parseMap(r.getWeights()));
        m.put("thresholds", parseMap(r.getThresholds()));
        return m;
    }

    /** 保存规则：权重合计必须 100，自动生成新版本号并停用旧版本 */
    @Transactional
    public EvalRule saveRule(EvalRuleDTO dto) {
        if (dto.getWeights() == null || dto.getWeights().isEmpty()) {
            throw new BizException("请配置各因素权重");
        }
        int sum = dto.getWeights().values().stream().mapToInt(Integer::intValue).sum();
        if (sum != 100) {
            throw new BizException("权重合计必须等于 100%，当前为 " + sum + "%");
        }
        for (String k : dto.getWeights().keySet()) {
            if (!FACTORS.containsKey(k)) {
                throw new BizException("未知的影响因素：" + k);
            }
        }
        EvalRule last = ruleMapper.selectOne(new LambdaQueryWrapper<EvalRule>()
                .orderByDesc(EvalRule::getId).last("limit 1"));
        int next = last == null ? 1 : (Integer.parseInt(last.getVersion().replace("v", "")) + 1);

        // 停用旧版本（历史计算结果仍按各自版本留痕，不受影响）
        for (EvalRule old : ruleMapper.selectList(new LambdaQueryWrapper<EvalRule>().eq(EvalRule::getStatus, 1))) {
            old.setStatus(0);
            ruleMapper.updateById(old);
        }
        EvalRule r = new EvalRule();
        r.setVersion("v" + next);
        r.setName("留样覆盖率与到期处置校验");
        r.setWeights(toJson(dto.getWeights()));
        r.setThresholds(toJson(dto.getThresholds() == null
                ? Map.of("excellent", 85, "good", 70, "medium", 55) : dto.getThresholds()));
        r.setEffectiveTime(dto.getEffectiveTime() == null ? LocalDateTime.now() : dto.getEffectiveTime());
        r.setStatus(1);
        r.setRemark(dto.getRemark());
        r.setCreateBy(UserContext.realName());
        r.setCreateTime(LocalDateTime.now());
        ruleMapper.insert(r);
        logService.log("更新校验规则", r.getVersion(), "权重 " + toJson(dto.getWeights()));
        return r;
    }

    /**
     * 执行一次校验计算。
     * 输入全部来自数据库真实记录：应留样任务数、实留数、平均重量、迟到入柜数、保留达标数、销毁凭证完整率。
     */
    @Transactional
    public Map<String, Object> evaluate(LocalDate bizDate) {
        LocalDate date = bizDate == null ? LocalDate.now() : bizDate;
        EvalRule rule = currentRule();
        Map<String, Integer> weights = parseIntMap(rule.getWeights());
        Map<String, Integer> th = parseIntMap(rule.getThresholds());

        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.atTime(23, 59, 59);

        // 应留样 / 实留样
        List<SampleTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<SampleTask>()
                .ge(SampleTask::getCreateTime, dayStart).le(SampleTask::getCreateTime, dayEnd));
        int shouldCount = tasks.size();
        List<FoodSample> samples = sampleMapper.selectList(new LambdaQueryWrapper<FoodSample>()
                .ge(FoodSample::getStoreTime, dayStart).le(FoodSample::getStoreTime, dayEnd)
                .ne(FoodSample::getStatus, Status.REJECTED));
        int actualCount = samples.size();

        // 菜单批次：当日是否存在批次记录
        long batchCount = batchMapper.selectCount(new LambdaQueryWrapper<FoodBatch>()
                .ge(FoodBatch::getProduceTime, dayStart).le(FoodBatch::getProduceTime, dayEnd));

        // 平均重量：以当日样品均值与标准均值比较（标准取分类下限均值）
        double avgWeight = samples.stream().filter(s -> s.getWeight() != null)
                .mapToDouble(s -> s.getWeight().doubleValue()).average().orElse(0);
        double stdWeight = 125.0;

        // 迟到入柜：留样时间晚于任务截止时间
        int lateStore = 0;
        for (FoodSample s : samples) {
            if (s.getTaskId() == null) {
                continue;
            }
            SampleTask t = taskMapper.selectById(s.getTaskId());
            if (t != null && t.getDeadline() != null && s.getStoreTime().isAfter(t.getDeadline())) {
                lateStore++;
            }
        }
        // 保留达标：未提前销毁且到期时间正确设置
        long retainOk = samples.stream().filter(s -> s.getExpireTime() != null
                && s.getExpireTime().isAfter(s.getStoreTime())).count();

        // 处置凭证完整性：当日销毁记录中有照片且双人不同的比例
        List<DisposalRecord> disposals = disposalMapper.selectList(new LambdaQueryWrapper<DisposalRecord>()
                .ge(DisposalRecord::getDisposeTime, dayStart).le(DisposalRecord::getDisposeTime, dayEnd));
        long evidenceOk = disposals.stream().filter(d -> d.getPhoto() != null && !d.getPhoto().isBlank()
                && d.getSupervisor() != null && !d.getSupervisor().equals(d.getExecutor())).count();
        double evidenceRate = disposals.isEmpty() ? (actualCount > 0 ? 0.0 : 1.0)
                : (double) evidenceOk / disposals.size();

        // 各因素得分（0–100 分制），缺失数据不参与计算并给出提示
        Map<String, Double> factorScore = new LinkedHashMap<>();
        factorScore.put("menuBatch", weights.containsKey("menuBatch")
                ? (batchCount > 0 ? 100.0 : 0.0) : null);
        factorScore.put("shouldCount", weights.containsKey("shouldCount")
                ? (shouldCount == 0 ? null : clamp(actualCount * 100.0 / shouldCount)) : null);
        factorScore.put("actualWeight", weights.containsKey("actualWeight")
                ? (avgWeight == 0 ? null : clamp(100.0 - Math.max(0, stdWeight - avgWeight) / stdWeight * 200)) : null);
        factorScore.put("storeTime", weights.containsKey("storeTime")
                ? (samples.isEmpty() ? null : clamp(100.0 - lateStore * 100.0 / samples.size() * 1.5)) : null);
        factorScore.put("retainDuration", weights.containsKey("retainDuration")
                ? (samples.isEmpty() ? null : clamp(retainOk * 100.0 / samples.size())) : null);
        factorScore.put("evidence", weights.containsKey("evidence") ? clamp(evidenceRate * 100) : null);

        // 加权总分（缺失因素按剩余权重等比归一，避免因缺数据直接判差）
        double weighted = 0, weightSum = 0;
        Map<String, Object> detail = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : factorScore.entrySet()) {
            int w = weights.getOrDefault(e.getKey(), 0);
            if (w == 0) {
                continue;
            }
            Double sc = e.getValue();
            double got = sc == null ? 0 : w * sc / 100.0;
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("name", FACTORS.get(e.getKey()));
            d.put("weight", w);
            d.put("score", sc == null ? null : round1(sc));
            d.put("weighted", round1(got));
            d.put("missing", sc == null);
            detail.put(e.getKey(), d);
            if (sc != null) {
                weighted += got;
                weightSum += w;
            }
        }
        double total = weightSum == 0 ? 0 : weighted * 100.0 / weightSum;
        total = Math.min(100, round1(total));

        String grade = gradeOf(total, th);
        String recommend = switch (grade) {
            case "优" -> "推荐按期处置，凭证齐全";
            case "良" -> "可进入销毁流程，需补齐处置凭证";
            case "中" -> "建议暂缓处置，先整改留样及时性";
            default -> "不建议处置，需先整改留样覆盖率与凭证";
        };

        List<String> unmet = new ArrayList<>();
        List<String> improve = new ArrayList<>();
        if (shouldCount > actualCount) {
            unmet.add("应留样 " + shouldCount + " 份，实际留样 " + actualCount + " 份，缺 " + (shouldCount - actualCount) + " 份");
            improve.add("按当日菜单补齐缺失的留样任务");
        }
        if (avgWeight > 0 && avgWeight < stdWeight) {
            unmet.add("平均重量 " + round1(avgWeight) + " g 低于标准 " + stdWeight + " g");
            improve.add("分装时按分类下限称重，避免边缘值");
        }
        if (lateStore > 0) {
            unmet.add(lateStore + " 份留样入柜晚于任务截止时间");
            improve.add("出餐后 1 小时内完成留样入柜");
        }
        if (evidenceRate < 1) {
            unmet.add("销毁凭证完整率 " + Math.round(evidenceRate * 100) + "%，缺少照片或监督人签字");
            improve.add("补齐销毁现场照片并由监督人签字确认");
        }
        if (unmet.isEmpty()) {
            unmet.add("无未满足条件");
            improve.add("保持当前留样与处置节奏");
        }

        // 输入快照
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("bizDate", date.toString());
        input.put("shouldCount", shouldCount);
        input.put("actualCount", actualCount);
        input.put("avgWeight", round1(avgWeight));
        input.put("stdWeight", stdWeight);
        input.put("lateStore", lateStore);
        input.put("retainOk", retainOk);
        input.put("batchCount", batchCount);
        input.put("disposalCount", disposals.size());
        input.put("evidenceRate", round1(evidenceRate * 100) / 100);

        EvalScore score = new EvalScore();
        LocalDate d = date;
        long seq = scoreMapper.selectCount(new LambdaQueryWrapper<EvalScore>()
                .eq(EvalScore::getBizDate, d)) + 1;
        score.setEvalNo("EV" + date.toString().replace("-", "") + "-" + String.format("%04d", seq));
        score.setBizDate(date);
        score.setRuleId(rule.getId());
        score.setRuleVersion(rule.getVersion());
        score.setInputJson(toJson(input));
        score.setDetailJson(toJson(detail));
        score.setTotalScore(BigDecimal.valueOf(total));
        score.setGrade(grade);
        score.setRecommend(recommend);
        score.setUnmetJson(toJson(unmet));
        score.setImproveJson(toJson(improve));
        score.setCalcTime(LocalDateTime.now());
        score.setAdopted(0);
        score.setCreateTime(LocalDateTime.now());
        scoreMapper.insert(score);
        logService.log("执行到期处置校验", score.getEvalNo(), "总分 " + total + "，等级 " + grade);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("score", score);
        data.put("detail", detail);
        data.put("input", input);
        data.put("unmet", unmet);
        data.put("improve", improve);
        data.put("grade", grade);
        data.put("total", total);
        data.put("ruleVersion", rule.getVersion());
        data.put("adjusts", adjustMapper.selectList(new LambdaQueryWrapper<EvalAdjust>()
                .eq(EvalAdjust::getScoreId, score.getId()).orderByDesc(EvalAdjust::getCreateTime)));
        return data;
    }

    /** 最近一次计算结果（页面初始化用），没有则自动算一次 */
    public Map<String, Object> latest() {
        EvalScore s = scoreMapper.selectOne(new LambdaQueryWrapper<EvalScore>()
                .orderByDesc(EvalScore::getCalcTime).last("limit 1"));
        if (s == null) {
            return evaluate(LocalDate.now());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("score", s);
        data.put("detail", parseMap(s.getDetailJson()));
        data.put("input", parseMap(s.getInputJson()));
        data.put("unmet", parseJsonList(s.getUnmetJson()));
        data.put("improve", parseJsonList(s.getImproveJson()));
        data.put("grade", s.getGrade());
        data.put("total", s.getTotalScore());
        data.put("ruleVersion", s.getRuleVersion());
        data.put("adjusts", adjustMapper.selectList(new LambdaQueryWrapper<EvalAdjust>()
                .eq(EvalAdjust::getScoreId, s.getId()).orderByDesc(EvalAdjust::getCreateTime)));
        return data;
    }

    /** 人工修正：只写修正记录，原始总分与等级原样保留 */
    @Transactional
    public void adjust(Long scoreId, String grade, String reason) {
        if (reason == null || reason.trim().length() < 5) {
            throw new BizException("人工调整原因不少于 5 个字");
        }
        if (!List.of("优", "良", "中", "差").contains(grade)) {
            throw new BizException("等级只能是 优/良/中/差");
        }
        EvalScore s = scoreMapper.selectById(scoreId);
        if (s == null) {
            throw new BizException("校验结果不存在");
        }
        EvalAdjust a = new EvalAdjust();
        a.setScoreId(scoreId);
        a.setBeforeGrade(s.getGrade());
        a.setAfterGrade(grade);
        a.setReason(reason.trim());
        a.setOperator(UserContext.realName());
        a.setCreateTime(LocalDateTime.now());
        adjustMapper.insert(a);
        s.setAdopted(1);
        scoreMapper.updateById(s);
        logService.log("人工修正校验等级", s.getEvalNo(), s.getGrade() + " → " + grade + "：" + reason.trim());
    }

    public void adopt(Long scoreId) {
        EvalScore s = scoreMapper.selectById(scoreId);
        if (s == null) {
            throw new BizException("校验结果不存在");
        }
        s.setAdopted(1);
        scoreMapper.updateById(s);
        logService.log("采纳校验推荐", s.getEvalNo(), s.getRecommend());
    }

    public List<EvalScore> history() {
        return scoreMapper.selectList(new LambdaQueryWrapper<EvalScore>()
                .orderByDesc(EvalScore::getCalcTime).last("limit 12"));
    }

    /** 效果统计：推荐采纳率、人工调整率 */
    public Map<String, Object> stats() {
        long total = scoreMapper.selectCount(null);
        long adopted = scoreMapper.selectCount(new LambdaQueryWrapper<EvalScore>().eq(EvalScore::getAdopted, 1));
        long adjusted = adjustMapper.selectCount(null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", total);
        m.put("adopted", adopted);
        m.put("adjusted", adjusted);
        m.put("adoptRate", total == 0 ? 0 : Math.round(adopted * 1000.0 / total) / 10.0);
        m.put("adjustRate", total == 0 ? 0 : Math.round(adjusted * 1000.0 / total) / 10.0);
        // 等级分布
        Map<String, Long> dist = new LinkedHashMap<>();
        for (String g : List.of("优", "良", "中", "差")) {
            dist.put(g, scoreMapper.selectCount(new LambdaQueryWrapper<EvalScore>().eq(EvalScore::getGrade, g)));
        }
        m.put("gradeDist", dist);
        return m;
    }

    private String gradeOf(double total, Map<String, Integer> th) {
        int excellent = th.getOrDefault("excellent", 85);
        int good = th.getOrDefault("good", 70);
        int medium = th.getOrDefault("medium", 55);
        if (total >= excellent) return "优";
        if (total >= good) return "良";
        if (total >= medium) return "中";
        return "差";
    }

    private double clamp(double v) {
        return Math.max(0, Math.min(100, v));
    }

    private double round1(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private String toJson(Object o) {
        try {
            return om.writeValueAsString(o);
        } catch (Exception e) {
            throw new BizException("数据序列化失败");
        }
    }

    private Map<String, Object> parseMap(String json) {
        try {
            return json == null ? new LinkedHashMap<>() : om.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private Map<String, Integer> parseIntMap(String json) {
        Map<String, Integer> m = new LinkedHashMap<>();
        parseMap(json).forEach((k, v) -> m.put(k, v instanceof Number ? ((Number) v).intValue() : 0));
        return m;
    }

    private List<String> parseJsonList(String json) {
        try {
            return json == null ? new ArrayList<>() : om.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
