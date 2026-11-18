package com.bioplatform.controller.admin;

import com.bioplatform.dto.common.ApiResponse;
import com.bioplatform.entity.Permission;
import com.bioplatform.mapper.PermissionMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 权限目录接口（只读树，33+28 条种子权限）。
 * 权限条目的增删改一期不开放。
 *
 * @author luosg
 */
@RestController
@PreAuthorize("hasAuthority('system:permission:list')")
@RequestMapping("/api/admin/permissions")
public class AdminPermissionController {

    /** 权限树节点 */
    public record PermTreeNode(Long id, String name, String permission, String type,
                               Long parentId, String path, Integer orderNum,
                               List<PermTreeNode> children) {
    }

    private final PermissionMapper permissionMapper;

    public AdminPermissionController(PermissionMapper permissionMapper) {
        this.permissionMapper = permissionMapper;
    }

    /**
     * 权限树（全部权限点，按 parent_id 组树）
     */
    @GetMapping("/tree")
    public ApiResponse<List<PermTreeNode>> tree() {
        List<Permission> all = permissionMapper.selectAll(new Permission());
        Map<Long, PermTreeNode> nodeById = new LinkedHashMap<>();
        for (Permission p : all) {
            nodeById.put(p.getId(), new PermTreeNode(
                    p.getId(), p.getName(), p.getPermission(), p.getType(),
                    p.getParentId(), p.getPath(), p.getOrderNum(), new ArrayList<>()));
        }
        List<PermTreeNode> roots = new ArrayList<>();
        for (Permission p : all) {
            PermTreeNode node = nodeById.get(p.getId());
            Long parentId = p.getParentId() == null ? 0L : p.getParentId();
            PermTreeNode parent = nodeById.get(parentId);
            if (parent != null && parentId != 0 && parentId != p.getId()) {
                parent.children().add(node);
            } else {
                roots.add(node);
            }
        }
        Comparator<PermTreeNode> byOrder = Comparator
                .comparing(PermTreeNode::orderNum, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(PermTreeNode::id);
        roots.sort(byOrder);
        for (PermTreeNode n : nodeById.values()) {
            n.children().sort(byOrder);
        }
        return ApiResponse.success(roots);
    }
}
