package com.lab.equipment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.lab.equipment.common.BusinessException;
import com.lab.equipment.entity.GroupMember;
import com.lab.equipment.entity.ResearchGroup;
import com.lab.equipment.entity.User;
import com.lab.equipment.mapper.GroupMemberMapper;
import com.lab.equipment.mapper.ResearchGroupMapper;
import com.lab.equipment.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 课题组服务（多成员模型）：
 * - 教师（或管理员）创建课题组并生成 8 位数字邀请码，创建者为本组负责人；
 * - 教师 / 学生均可凭邀请码加入，且可同时加入多个课题组（含教师加入别人的组）；
 * - 课题组为选填，不加入不影响正常预约；
 * - 课题组只共享【预约信息】，损耗记录、信用积分严格隔离。
 */
@Service
@RequiredArgsConstructor
public class GroupService {

    private final ResearchGroupMapper groupMapper;
    private final GroupMemberMapper memberMapper;
    private final UserMapper userMapper;
    private final OpLogService opLogService;
    /** 邀请码：纯数字（6-8 位数字要求，取 8 位），避开 0/1 开头歧义不做处理，字符串存储 */
    private static final String CODE_CHARS = "0123456789";
    private static final int CODE_LEN = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 创建课题组（仅教师），创建者为本组负责人，返回含邀请码 */
    @Transactional
    public ResearchGroup create(User owner, String name, String description) {
        if (!"TEACHER".equals(owner.getRole())) {
            throw new BusinessException("仅教师可以创建课题组");
        }
        if (StringUtils.isBlank(name)) {
            throw new BusinessException("请填写课题组名称");
        }
        ResearchGroup group = new ResearchGroup();
        group.setName(name.trim());
        group.setDescription(StringUtils.isBlank(description) ? null : description.trim());
        group.setOwnerId(owner.getId());
        group.setInviteCode(generateCode());
        groupMapper.insert(group);
        return group;
    }

    /** 我名下（我创建的）课题组列表，附成员数与邀请码 */
    public List<Map<String, Object>> ownedGroups(User owner) {
        List<ResearchGroup> groups = groupMapper.selectList(new LambdaQueryWrapper<ResearchGroup>()
                .eq(ResearchGroup::getOwnerId, owner.getId())
                .orderByAsc(ResearchGroup::getId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (ResearchGroup g : groups) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", g.getId());
            item.put("name", g.getName());
            item.put("description", g.getDescription());
            item.put("inviteCode", g.getInviteCode());
            item.put("createTime", g.getCreateTime());
            item.put("memberCount", memberCount(g.getId()));
            result.add(item);
        }
        return result;
    }

    /**
     * 某课题组成员列表（仅本组负责人可用；管理员 / 保管员 / 安全员即使在组内也无权查看与管理）。
     * 返回精简字段：姓名、学号/工号、角色、加入时间。
     */
    public List<Map<String, Object>> members(Long groupId, User requester) {
        requireOwned(groupId, requester);
        List<Map<String, Object>> result = new ArrayList<>();
        for (GroupMember gm : memberMapper.selectList(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getGroupId, groupId)
                .orderByAsc(GroupMember::getId))) {
            User u = userMapper.selectById(gm.getUserId());
            if (u == null) continue;
            Map<String, Object> item = new HashMap<>();
            item.put("id", u.getId());
            item.put("username", u.getUsername());
            item.put("realName", StringUtils.isNotBlank(u.getRealName()) ? u.getRealName() : u.getUsername());
            item.put("employeeNo", u.getEmployeeNo());
            item.put("role", u.getRole());
            item.put("department", u.getDepartment());
            item.put("joinTime", gm.getJoinTime());
            result.add(item);
        }
        return result;
    }

    /**
     * 凭邀请码加入课题组；可同时加入多个课题组（多成员模型）。
     * 同一组不能重复加入；负责人无需（也不能）凭码加入自己的组。
     */
    @Transactional
    public ResearchGroup join(User user, String inviteCode) {
        if (StringUtils.isBlank(inviteCode)) {
            throw new BusinessException("请输入邀请码");
        }
        ResearchGroup group = groupMapper.selectOne(new LambdaQueryWrapper<ResearchGroup>()
                .eq(ResearchGroup::getInviteCode, inviteCode.trim().toUpperCase()));
        if (group == null) {
            throw new BusinessException("邀请码无效，请核对后重试");
        }
        if (group.getOwnerId().equals(user.getId())) {
            throw new BusinessException("您是该课题组负责人，无需加入");
        }
        if (isMember(group.getId(), user.getId())) {
            throw new BusinessException("您已加入课题组「" + group.getName() + "」，无需重复加入");
        }
        GroupMember gm = new GroupMember();
        gm.setGroupId(group.getId());
        gm.setUserId(user.getId());
        memberMapper.insert(gm);
        return group;
    }

    /** 退出指定课题组 */
    @Transactional
    public void leave(User user, Long groupId) {
        if (groupId == null || !isMember(groupId, user.getId())) {
            throw new BusinessException("您未加入该课题组");
        }
        removeMembership(groupId, user.getId());
    }

    /** 负责人移除组员（移除后该成员立即失去本组预约查看权限，不影响账号与其他组） */
    @Transactional
    public void removeMember(Long groupId, Long memberId, User requester) {
        ResearchGroup group = requireOwned(groupId, requester);
        if (!isMember(groupId, memberId)) {
            throw new BusinessException("该用户不是本组成员");
        }
        User member = userMapper.selectById(memberId);
        removeMembership(groupId, memberId);
        opLogService.log("移除课题组成员", "课题组「" + group.getName() + "」移出成员："
                + (member == null ? memberId
                : (StringUtils.isNotBlank(member.getRealName()) ? member.getRealName() : member.getUsername())));
    }

    /** 重新生成邀请码（负责人） */
    @Transactional
    public String regenerateCode(Long groupId, User requester) {
        ResearchGroup group = requireOwned(groupId, requester);
        group.setInviteCode(generateCode());
        groupMapper.updateById(group);
        return group.getInviteCode();
    }

    /** 我加入的课题组列表（别人创建的；含负责人姓名与成员数；可同时多个） */
    public List<Map<String, Object>> myGroups(User user) {
        List<GroupMember> memberships = memberMapper.selectList(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getUserId, user.getId())
                .orderByAsc(GroupMember::getId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (GroupMember gm : memberships) {
            ResearchGroup group = groupMapper.selectById(gm.getGroupId());
            if (group == null || group.getOwnerId().equals(user.getId())) continue; // 自己创建的归入「我负责」
            Map<String, Object> info = new HashMap<>();
            info.put("id", group.getId());
            info.put("name", group.getName());
            info.put("description", group.getDescription());
            info.put("ownerId", group.getOwnerId());
            User owner = userMapper.selectById(group.getOwnerId());
            info.put("ownerName", owner != null
                    ? (StringUtils.isNotBlank(owner.getRealName()) ? owner.getRealName() : owner.getUsername())
                    : "未知");
            info.put("memberCount", memberCount(group.getId()));
            info.put("joinTime", gm.getJoinTime());
            result.add(info);
        }
        return result;
    }

    /* ============ 预约数据可见域核心方法 ============ */

    /**
     * 当前用户可看到的「预约」数据所属用户 ID 集合。
     * 返回 null 表示全部可见（管理员 / 保管员 / 安全员）；
     * 其余角色 = 本人
     *   + 我负责的每个课题组的全部成员（负责人可见本组所有人预约）
     *   + 我加入的每个课题组的全部成员与负责人（普通组员可见同组成员预约）。
     * 注意：仅共享预约信息，损耗 / 信用积分不适用本方法。
     */
    public List<Long> visibleReservationUserIds(User user) {
        String role = user == null ? null : user.getRole();
        if ("ADMIN".equals(role) || "KEEPER".equals(role) || "SAFETY".equals(role)) {
            return null;
        }
        Set<Long> ids = new LinkedHashSet<>();
        ids.add(user.getId());
        // 我负责的课题组 → 全部成员
        for (ResearchGroup g : groupMapper.selectList(new LambdaQueryWrapper<ResearchGroup>()
                .eq(ResearchGroup::getOwnerId, user.getId()))) {
            for (User m : usersOf(g.getId())) ids.add(m.getId());
        }
        // 我加入的课题组 → 全部成员 + 负责人
        for (GroupMember gm : memberMapper.selectList(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getUserId, user.getId()))) {
            for (User m : usersOf(gm.getGroupId())) ids.add(m.getId());
            ResearchGroup g = groupMapper.selectById(gm.getGroupId());
            if (g != null) ids.add(g.getOwnerId());
        }
        return new ArrayList<>(ids);
    }

    /** 某课题组的全部成员 ID（不含负责人；供统计用） */
    public List<Long> memberIds(Long groupId) {
        List<Long> ids = new ArrayList<>();
        for (GroupMember gm : memberMapper.selectList(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getGroupId, groupId))) {
            ids.add(gm.getUserId());
        }
        return ids;
    }

    public long memberCount(Long groupId) {
        return memberMapper.selectCount(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getGroupId, groupId));
    }

    /** 某课题组全部用户（成员关系表 JOIN，按加入顺序） */
    private List<User> usersOf(Long groupId) {
        List<User> users = new ArrayList<>();
        for (GroupMember gm : memberMapper.selectList(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getGroupId, groupId)
                .orderByAsc(GroupMember::getId))) {
            User u = userMapper.selectById(gm.getUserId());
            if (u != null) users.add(u);
        }
        return users;
    }

    private boolean isMember(Long groupId, Long userId) {
        return memberMapper.selectCount(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUserId, userId)) > 0;
    }

    private void removeMembership(Long groupId, Long userId) {
        memberMapper.delete(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUserId, userId));
    }

    /** 校验 requester 必须是课题组负责人本人（管理员等角色即使入组也不得管理） */
    private ResearchGroup requireOwned(Long groupId, User requester) {
        ResearchGroup group = groupMapper.selectById(groupId);
        if (group == null) throw new BusinessException("课题组不存在");
        if (!group.getOwnerId().equals(requester.getId())) {
            throw new BusinessException("无权限：仅课题组负责人可管理组员");
        }
        return group;
    }

    /** 生成 8 位纯数字邀请码（撞码重试） */
    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LEN);
        for (int i = 0; i < CODE_LEN; i++) {
            sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        if (groupMapper.selectCount(new LambdaQueryWrapper<ResearchGroup>()
                .eq(ResearchGroup::getInviteCode, sb.toString())) > 0) {
            return generateCode();
        }
        return sb.toString();
    }
}
