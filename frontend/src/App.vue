<template>
  <router-view />
</template>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
html, body, #app {
  height: 100%;
  font-family: 'Helvetica Neue', Arial, 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: 14px;
  color: #333333;
}
.page-card {
  background: rgba(255, 255, 255, 0.97);
  border: 1px solid #eaf0f3;
  border-radius: 14px;
  padding: 22px;
  box-shadow: 0 4px 16px rgba(150, 165, 175, 0.08);
  color: #333333;
}
.page-title {
  font-size: 16px;
  font-weight: 650;
  margin-bottom: 18px;
  color: #333333;
  letter-spacing: 0.3px;
}
.toolbar {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 16px;
  align-items: center;
  color: #333333;
}

/* ---------- 全局可读性兜底 ---------- */
/* 辅助说明类：不小于 12px 且统一 #666 */
.sub,
.muted,
.hint,
.desc,
.tip,
.note,
.meta {
  font-size: 12.5px;
  color: #666666;
}
/* 空状态 / 占位统一 #777 */
.empty-text,
.placeholder-text,
.no-data {
  font-size: 13px;
  color: #777777;
}
/* 禁止出现 11px 及以下的阅读文字（装饰性英文除外） */
@media screen {
  .page-card p,
  .page-card span,
  .page-card div,
  .page-card label,
  .page-card td {
    letter-spacing: 0.1px;
  }
}
</style>
