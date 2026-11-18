package com.lab.equipment.controller;

import com.lab.equipment.common.Result;
import com.lab.equipment.common.UserContext;
import com.lab.equipment.entity.ResearchGroup;
import com.lab.equipment.entity.User;
import com.lab.equipment.service.GroupService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 课题组：教师创建（负责人）；成员凭邀请码加入，可同时加入多个组 */
@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    /** 创建课题组（教师 / 管理员），创建者为本组负责人 */
    @PostMapping
    public Result<ResearchGroup> create(@RequestBody CreateDTO dto) {
        return Result.ok(groupService.create(UserContext.get(), dto.getName(), dto.getDescription()));
    }

    /** 我名下（我创建的）课题组列表 */
    @GetMapping("/owned")
    public Result<List<Map<String, Object>>> owned() {
        return Result.ok(groupService.ownedGroups(UserContext.get()));
    }

    /** 某课题组成员列表（仅本组负责人可用，其余角色返回 400 无权限） */
    @GetMapping("/{id}/members")
    public Result<List<Map<String, Object>>> members(@PathVariable Long id) {
        return Result.ok(groupService.members(id, UserContext.get()));
    }

    /** 凭邀请码加入课题组（可同时加入多个） */
    @PostMapping("/join")
    public Result<ResearchGroup> join(@RequestBody JoinDTO dto) {
        return Result.ok(groupService.join(UserContext.get(), dto.getInviteCode()));
    }

    /** 我加入的课题组列表（别人创建的；可同时多个） */
    @GetMapping("/mine")
    public Result<List<Map<String, Object>>> mine() {
        return Result.ok(groupService.myGroups(UserContext.get()));
    }

    /** 退出指定课题组 */
    @PostMapping("/{id}/leave")
    public Result<Void> leave(@PathVariable Long id) {
        groupService.leave(UserContext.get(), id);
        return Result.ok();
    }

    /** 移除组员（负责人） */
    @DeleteMapping("/{id}/members/{memberId}")
    public Result<Void> removeMember(@PathVariable Long id, @PathVariable Long memberId) {
        groupService.removeMember(id, memberId, UserContext.get());
        return Result.ok();
    }

    /** 重新生成邀请码（负责人） */
    @PutMapping("/{id}/invite-code")
    public Result<String> regenerate(@PathVariable Long id) {
        return Result.ok(groupService.regenerateCode(id, UserContext.get()));
    }

    @Data
    public static class CreateDTO {
        private String name;
        private String description;
    }

    @Data
    public static class JoinDTO {
        private String inviteCode;
    }
}
