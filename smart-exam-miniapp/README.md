# smart-exam-miniapp

微信小程序学生端，面向 `smart-exam-cloud` 的学生考试主流程。当前只做学生端，不包含教师端和管理后台。

## 当前能力

- 学生账号密码登录
- 获取学生本人信息
- 查看我的考试列表
- 开始或继续考试
- 拉取试卷和历史答案
- 本地草稿恢复与自动保存
- 保存答案、提交试卷
- 查看成绩、题目得分和解析开放状态
- 离开答题页、断网等基础防作弊事件上报

## 目录结构

```text
smart-exam-miniapp/
  app.js
  app.json
  app.wxss
  project.config.json
  sitemap.json
  config/
    index.js
  services/
    api.js
    request.js
  utils/
    exam.js
    format.js
    storage.js
  pages/
    login/
    exams/
    session/
    result/
```

## 使用方式

1. 用微信开发者工具打开本目录 `smart-exam-miniapp`
2. 修改 `config/index.js` 里的 `apiBaseUrl`
3. 确认后端网关可访问
4. 登录学生账号后进入考试列表

## 联调说明

- 默认按后端网关接口前缀 `/api/v1` 设计
- 当前是账号密码登录，不是微信授权登录
- 本地开发可使用 `http://127.0.0.1:9000/api/v1`
- 真机和发布前需要把网关配置为 HTTPS 合法域名，并在微信后台配置 request 合法域名

## 后续增强

- 增加微信登录绑定能力
- 增加更完整的设备状态检查
- 增加更细的防作弊事件采集
- 增加小程序端自动化测试或开发者工具预览检查流程
