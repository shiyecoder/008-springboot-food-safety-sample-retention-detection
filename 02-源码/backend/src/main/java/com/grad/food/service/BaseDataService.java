package com.grad.food.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.grad.food.common.BizException;
import com.grad.food.common.PageResult;
import com.grad.food.common.Status;
import com.grad.food.common.UserContext;
import com.grad.food.entity.*;
import com.grad.food.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 基础数据：食品分类、供应单位、加工档口、冷藏柜与仓位、生产批次。
 * 用 type 分发到对应实体，避免为每张基础表写一套重复的 CRUD。
 */
@Service
@RequiredArgsConstructor
public class BaseDataService {

    private final FoodCategoryMapper categoryMapper;
    private final SupplierMapper supplierMapper;
    private final WorkshopMapper workshopMapper;
    private final StorageLocationMapper locationMapper;
    private final FoodBatchMapper batchMapper;
    private final SampleRuleMapper ruleMapper;
    private final SysOrgMapper orgMapper;
    private final FoodSampleMapper sampleMapper;
    private final LogService logService;

    public PageResult<?> page(String type, long page, long size, String keyword) {
        switch (type) {
            case "category" -> {
                LambdaQueryWrapper<FoodCategory> w = new LambdaQueryWrapper<FoodCategory>()
                        .like(keyword != null && !keyword.isBlank(), FoodCategory::getName, keyword)
                        .orderByAsc(FoodCategory::getSort);
                Page<FoodCategory> p = categoryMapper.selectPage(new Page<>(page, size), w);
                return PageResult.of(p.getTotal(), page, size, p.getRecords());
            }
            case "supplier" -> {
                LambdaQueryWrapper<Supplier> w = new LambdaQueryWrapper<Supplier>()
                        .like(keyword != null && !keyword.isBlank(), Supplier::getName, keyword)
                        .orderByDesc(Supplier::getId);
                Page<Supplier> p = supplierMapper.selectPage(new Page<>(page, size), w);
                return PageResult.of(p.getTotal(), page, size, p.getRecords());
            }
            case "workshop" -> {
                LambdaQueryWrapper<Workshop> w = new LambdaQueryWrapper<Workshop>()
                        .like(keyword != null && !keyword.isBlank(), Workshop::getName, keyword)
                        .orderByAsc(Workshop::getId);
                Page<Workshop> p = workshopMapper.selectPage(new Page<>(page, size), w);
                return PageResult.of(p.getTotal(), page, size, p.getRecords());
            }
            case "location" -> {
                LambdaQueryWrapper<StorageLocation> w = new LambdaQueryWrapper<StorageLocation>()
                        .like(keyword != null && !keyword.isBlank(), StorageLocation::getCode, keyword)
                        .orderByAsc(StorageLocation::getCabinetNo)
                        .orderByAsc(StorageLocation::getLayerNo)
                        .orderByAsc(StorageLocation::getSlotNo);
                Page<StorageLocation> p = locationMapper.selectPage(new Page<>(page, size), w);
                return PageResult.of(p.getTotal(), page, size, p.getRecords());
            }
            case "batch" -> {
                LambdaQueryWrapper<FoodBatch> w = new LambdaQueryWrapper<FoodBatch>()
                        .and(keyword != null && !keyword.isBlank(),
                                q -> q.like(FoodBatch::getBatchNo, keyword).or().like(FoodBatch::getName, keyword))
                        .orderByDesc(FoodBatch::getProduceTime);
                Page<FoodBatch> p = batchMapper.selectPage(new Page<>(page, size), w);
                return PageResult.of(p.getTotal(), page, size, p.getRecords());
            }
            case "rule" -> {
                LambdaQueryWrapper<SampleRule> w = new LambdaQueryWrapper<SampleRule>()
                        .orderByAsc(SampleRule::getCategoryId);
                Page<SampleRule> p = ruleMapper.selectPage(new Page<>(page, size), w);
                return PageResult.of(p.getTotal(), page, size, p.getRecords());
            }
            default -> throw new BizException("未知的基础数据类型：" + type);
        }
    }

    public void save(String type, Map<String, Object> body) {
        Long id = body.get("id") == null ? null : Long.valueOf(String.valueOf(body.get("id")));
        switch (type) {
            case "category" -> {
                FoodCategory e = id == null ? new FoodCategory() : categoryMapper.selectById(id);
                e.setCode(str(body, "code"));
                e.setName(str(body, "name"));
                e.setMinWeight(dec(body, "minWeight"));
                e.setStorageCond(str(body, "storageCond"));
                e.setRetainHours(num(body, "retainHours"));
                e.setMeals(str(body, "meals"));
                e.setOwner(str(body, "owner"));
                e.setStatus(num(body, "status") == null ? 1 : num(body, "status"));
                e.setSort(num(body, "sort") == null ? 0 : num(body, "sort"));
                e.setUpdateTime(LocalDateTime.now());
                if (id == null) {
                    e.setCreateTime(LocalDateTime.now());
                    categoryMapper.insert(e);
                } else {
                    categoryMapper.updateById(e);
                }
            }
            case "supplier" -> {
                Supplier e = id == null ? new Supplier() : supplierMapper.selectById(id);
                e.setName(str(body, "name"));
                e.setContact(str(body, "contact"));
                e.setPhone(str(body, "phone"));
                e.setLicenseNo(str(body, "licenseNo"));
                e.setCategory(str(body, "category"));
                e.setStatus(num(body, "status") == null ? 1 : num(body, "status"));
                e.setUpdateTime(LocalDateTime.now());
                if (id == null) {
                    e.setCreateTime(LocalDateTime.now());
                    supplierMapper.insert(e);
                } else {
                    supplierMapper.updateById(e);
                }
            }
            case "workshop" -> {
                Workshop e = id == null ? new Workshop() : workshopMapper.selectById(id);
                e.setName(str(body, "name"));
                e.setOrgId(body.get("orgId") == null ? null : Long.valueOf(String.valueOf(body.get("orgId"))));
                e.setManager(str(body, "manager"));
                e.setPhone(str(body, "phone"));
                e.setStatus(num(body, "status") == null ? 1 : num(body, "status"));
                e.setUpdateTime(LocalDateTime.now());
                if (id == null) {
                    e.setCreateTime(LocalDateTime.now());
                    workshopMapper.insert(e);
                } else {
                    workshopMapper.updateById(e);
                }
            }
            case "location" -> {
                StorageLocation e = id == null ? new StorageLocation() : locationMapper.selectById(id);
                e.setCabinetNo(str(body, "cabinetNo"));
                e.setLayerNo(num(body, "layerNo"));
                e.setSlotNo(num(body, "slotNo"));
                String code = (str(body, "cabinetNo") == null ? "" : str(body, "cabinetNo"))
                        + "-" + num(body, "layerNo") + "-" + String.format("%02d", num(body, "slotNo") == null ? 0 : num(body, "slotNo"));
                e.setCode(code);
                e.setLocationDesc(str(body, "locationDesc"));
                e.setStatus(num(body, "status") == null ? 1 : num(body, "status"));
                e.setOccupied(e.getId() == null ? 0 : e.getOccupied());
                e.setUpdateTime(LocalDateTime.now());
                if (id == null) {
                    e.setCreateTime(LocalDateTime.now());
                    locationMapper.insert(e);
                } else {
                    locationMapper.updateById(e);
                }
            }
            case "batch" -> {
                FoodBatch e = id == null ? new FoodBatch() : batchMapper.selectById(id);
                e.setBatchNo(str(body, "batchNo"));
                e.setName(str(body, "name"));
                e.setMeal(str(body, "meal"));
                e.setWorkshopId(body.get("workshopId") == null ? null : Long.valueOf(String.valueOf(body.get("workshopId"))));
                e.setSupplierId(body.get("supplierId") == null ? null : Long.valueOf(String.valueOf(body.get("supplierId"))));
                e.setProduceTime(LocalDateTime.parse(String.valueOf(body.get("produceTime")).replace(" ", "T")));
                e.setManager(str(body, "manager"));
                e.setMenuCode(str(body, "menuCode"));
                e.setDishCount(num(body, "dishCount") == null ? 0 : num(body, "dishCount"));
                e.setStatus(str(body, "status") == null ? "RUNNING" : str(body, "status"));
                e.setRemark(str(body, "remark"));
                e.setUpdateTime(LocalDateTime.now());
                if (id == null) {
                    e.setCreateTime(LocalDateTime.now());
                    batchMapper.insert(e);
                } else {
                    batchMapper.updateById(e);
                }
            }
            default -> throw new BizException("未知的基础数据类型：" + type);
        }
        logService.log("保存基础数据", type, "id=" + id);
    }

    /** 删除前校验引用：分类被留样引用、仓位有占用时禁止删除 */
    public void remove(String type, Long id) {
        switch (type) {
            case "category" -> {
                long used = sampleMapper.selectCount(new LambdaQueryWrapper<FoodSample>()
                        .eq(FoodSample::getCategoryId, id));
                if (used > 0) {
                    throw new BizException("该分类已被 " + used + " 条留样引用，不能删除");
                }
                categoryMapper.deleteById(id);
            }
            case "supplier" -> supplierMapper.deleteById(id);
            case "workshop" -> workshopMapper.deleteById(id);
            case "location" -> {
                StorageLocation loc = locationMapper.selectById(id);
                if (loc != null && loc.getOccupied() != null && loc.getOccupied() == 1) {
                    throw new BizException("仓位正在使用中，不能删除");
                }
                locationMapper.deleteById(id);
            }
            case "batch" -> batchMapper.deleteById(id);
            default -> throw new BizException("该类型不支持删除");
        }
        logService.log("删除基础数据", type, "id=" + id, false);
    }

    /** 下拉选项：一次返回所有基础数据，前端登录后缓存 */
    public Map<String, Object> options() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("categories", categoryMapper.selectList(new LambdaQueryWrapper<FoodCategory>()
                .eq(FoodCategory::getStatus, 1).orderByAsc(FoodCategory::getSort)));
        m.put("suppliers", supplierMapper.selectList(new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getStatus, 1)));
        m.put("workshops", workshopMapper.selectList(new LambdaQueryWrapper<Workshop>()
                .eq(Workshop::getStatus, 1)));
        m.put("orgs", orgMapper.selectList(new LambdaQueryWrapper<SysOrg>().orderByAsc(SysOrg::getSort)));
        m.put("locations", locationMapper.selectList(new LambdaQueryWrapper<StorageLocation>()
                .eq(StorageLocation::getStatus, 1)
                .orderByAsc(StorageLocation::getCabinetNo)
                .orderByAsc(StorageLocation::getLayerNo)
                .orderByAsc(StorageLocation::getSlotNo)));
        m.put("batches", batchMapper.selectList(new LambdaQueryWrapper<FoodBatch>()
                .orderByDesc(FoodBatch::getProduceTime).last("limit 30")));
        m.put("rules", ruleMapper.selectList(new LambdaQueryWrapper<SampleRule>().eq(SampleRule::getStatus, 1)));
        m.put("meals", List.of("早餐", "午餐", "晚餐"));
        m.put("inspectTypes", List.of("感官", "微生物", "添加剂", "综合"));
        m.put("disposalMethods", List.of("餐厨垃圾统一处理", "高温灭菌后处理", "专业机构回收"));
        m.put("sampleStatuses", List.of(
                Map.of("value", "PENDING_REVIEW", "label", "待复核"),
                Map.of("value", "RETAINING", "label", "保留中"),
                Map.of("value", "PENDING_DISPOSE", "label", "待销毁"),
                Map.of("value", "DISPOSED", "label", "已销毁"),
                Map.of("value", "REJECTED", "label", "已驳回")));
        return m;
    }

    /** 柜位图：按冷藏柜分组返回仓位与占用样品 */
    public Map<String, Object> cabinets() {
        List<StorageLocation> locs = locationMapper.selectList(new LambdaQueryWrapper<StorageLocation>()
                .orderByAsc(StorageLocation::getCabinetNo)
                .orderByAsc(StorageLocation::getLayerNo)
                .orderByAsc(StorageLocation::getSlotNo));
        List<FoodSample> active = sampleMapper.selectList(new LambdaQueryWrapper<FoodSample>()
                .in(FoodSample::getStatus, "RETAINING", "PENDING_REVIEW", "PENDING_DISPOSE"));
        Map<Long, FoodSample> byLoc = new HashMap<>();
        active.forEach(s -> {
            if (s.getLocationId() != null) {
                byLoc.put(s.getLocationId(), s);
            }
        });
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (StorageLocation l : locs) {
            FoodSample s = byLoc.get(l.getId());
            Map<String, Object> cell = new LinkedHashMap<>();
            cell.put("id", l.getId());
            cell.put("code", l.getCode());
            cell.put("layer", l.getLayerNo());
            cell.put("slot", l.getSlotNo());
            cell.put("occupied", s != null);
            cell.put("sampleId", s == null ? null : s.getId());
            cell.put("foodName", s == null ? null : s.getFoodName());
            cell.put("status", s == null ? null : s.getStatus());
            cell.put("statusLabel", s == null ? null : Status.sampleLabel(s.getStatus()));
            cell.put("locked", s == null ? 0 : s.getLocked());
            cell.put("storeTime", s == null ? null : s.getStoreTime());
            cell.put("expireTime", s == null ? null : s.getExpireTime());
            boolean due = s != null && s.getExpireTime() != null
                    && s.getExpireTime().isBefore(LocalDateTime.now().plusHours(24));
            cell.put("due", due);
            grouped.computeIfAbsent(l.getCabinetNo(), k -> new ArrayList<>()).add(cell);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cabinets", grouped);
        data.put("total", locs.size());
        data.put("occupied", byLoc.size());
        return data;
    }

    private String str(Map<String, Object> b, String k) {
        Object v = b.get(k);
        return v == null ? null : String.valueOf(v);
    }

    private Integer num(Map<String, Object> b, String k) {
        Object v = b.get(k);
        if (v == null || String.valueOf(v).isBlank()) {
            return null;
        }
        return (int) Double.parseDouble(String.valueOf(v));
    }

    private java.math.BigDecimal dec(Map<String, Object> b, String k) {
        Object v = b.get(k);
        if (v == null || String.valueOf(v).isBlank()) {
            return null;
        }
        return new java.math.BigDecimal(String.valueOf(v));
    }
}
