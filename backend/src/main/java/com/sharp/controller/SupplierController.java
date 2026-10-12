package com.sharp.controller;

import com.sharp.common.CurrentUser;
import com.sharp.common.Result;
import com.sharp.dto.SupplierDupCheck;
import com.sharp.dto.SupplierParseRequest;
import com.sharp.entity.Supplier;
import com.sharp.service.SupplierService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/supplier")
public class SupplierController {

    private final SupplierService service;

    public SupplierController(SupplierService service) {
        this.service = service;
    }

    /** 解析预览（不落库） */
    @PostMapping("/parse")
    public Result<List<Supplier>> parse(@Valid @RequestBody SupplierParseRequest req) {
        return Result.ok(service.parse(req.getRawData(), req.getFields()));
    }

    /** 查重（不落库）：返回重复 / 新数据条数与重复行。 */
    @PostMapping("/check")
    public Result<SupplierDupCheck> check(@Valid @RequestBody SupplierParseRequest req) {
        return Result.ok(service.check(req.getRawData(), req.getFields()));
    }

    /** 解析并保存。录入人取自登录用户。skipDuplicates=true 时跳过重复。 */
    @PostMapping("/save")
    public Result<List<Supplier>> save(@Valid @RequestBody SupplierParseRequest req) {
        String createdBy = CurrentUser.get();
        if (createdBy == null || createdBy.isBlank()) {
            createdBy = "unknown";
        }
        return Result.ok(service.parseAndSave(req.getRawData(), req.getFields(), req.isSkipDuplicates(), createdBy));
    }

    /** 分页查询 */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<Supplier> result = service.list(keyword, page, size);
        Map<String, Object> data = new HashMap<>();
        data.put("records", result.getContent());
        data.put("total", result.getTotalElements());
        data.put("page", page);
        data.put("size", size);
        return Result.ok(data);
    }

    /** 更新单条 */
    @PutMapping("/{id}")
    public Result<Supplier> update(@PathVariable Long id, @RequestBody Supplier supplier) {
        supplier.setId(id);
        return Result.ok(service.update(supplier));
    }

    /** 删除 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }
}
