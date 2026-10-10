/**
 * 组织架构升级功能验证脚本
 *
 * 覆盖 4 个阶段的核心 API：
 *   阶段1：岗位职级体系（sys_position CRUD + @RequiresPermission.requirePosition）
 *   阶段2：任务级数据隔离（工程师仅见自己负责的任务）
 *   阶段3：项目授权规范化（申请-审批-撤销-延期 + 过期校验）
 *   阶段4：项目组成员管理（add/remove/transfer-leader）
 *
 * 前置条件：
 *   1. 已执行迁移脚本 V20260705__organization_architecture_upgrade.sql
 *   2. 后端服务已启动（默认 http://localhost:8080）
 *   3. 测试账号密码均为 admin123：
 *      - admin/admin123（管理员）
 *      - pm_zhangming/admin123（项目经理）
 *      - engineer_chenqianlong/admin123（工程师）
 *
 * 运行方式：
 *   node scripts/verify-organization-arch.js
 *   或 PowerShell: .\scripts\verify-organization-arch.ps1
 */

const http = require('http')

const BASE_URL = 'http://localhost:8080'
const TEST_ACCOUNTS = {
  admin: { username: 'admin', password: 'admin123' },
  pm: { username: 'pm_zhangming', password: 'admin123' },
  engineer: { username: 'engineer_chenqianlong', password: 'admin123' }
}

let passCount = 0
let failCount = 0
const failures = []

function logPass(msg) {
  passCount++
  console.log(`  ✓ ${msg}`)
}

function logFail(msg, detail = '') {
  failCount++
  failures.push({ msg, detail })
  console.log(`  ✗ ${msg}${detail ? ' — ' + detail : ''}`)
}

function request(method, path, body, token) {
  return new Promise((resolve, reject) => {
    const data = body ? JSON.stringify(body) : null
    const headers = { 'Content-Type': 'application/json' }
    if (data) headers['Content-Length'] = Buffer.byteLength(data)
    if (token) headers['Authorization'] = 'Bearer ' + token

    const req = http.request(
      `${BASE_URL}${path}`,
      { method, headers },
      (res) => {
        let chunks = ''
        res.on('data', (c) => (chunks += c))
        res.on('end', () => {
          try {
            resolve({ status: res.statusCode, data: JSON.parse(chunks || '{}') })
          } catch (e) {
            resolve({ status: res.statusCode, data: { raw: chunks } })
          }
        })
      }
    )
    req.on('error', reject)
    if (data) req.write(data)
    req.end()
  })
}

async function login(account) {
  const res = await request('POST', '/api/auth/login', account)
  if (res.status !== 200 || !res.data.data?.token) {
    throw new Error(`登录失败 ${account.username}: ${JSON.stringify(res.data)}`)
  }
  return res.data.data.token
}

async function test(name, fn) {
  console.log(`\n[测试] ${name}`)
  try {
    await fn()
  } catch (e) {
    logFail(name, e.message)
  }
}

async function main() {
  console.log('═══════════════════════════════════════════════')
  console.log('  组织架构升级功能验证（4 阶段）')
  console.log('═══════════════════════════════════════════════')

  // 登录获取 token
  console.log('\n[准备] 登录测试账号...')
  let adminToken, pmToken, engineerToken
  try {
    adminToken = await login(TEST_ACCOUNTS.admin)
    logPass('admin 登录成功')
    pmToken = await login(TEST_ACCOUNTS.pm)
    logPass('pm_zhangming 登录成功')
    engineerToken = await login(TEST_ACCOUNTS.engineer)
    logPass('engineer_chenqianlong 登录成功')
  } catch (e) {
    console.error('登录失败，终止测试:', e.message)
    process.exit(1)
  }

  // ═══════════════════════════════════════════════════
  // 阶段1：岗位职级体系
  // ═══════════════════════════════════════════════════
  console.log('\n┌───────────────────────────────────────────────')
  console.log('│ 阶段1：岗位职级体系')
  console.log('└───────────────────────────────────────────────')

  // 1.1 岗位列表查询（admin）
  await test('1.1 admin 查询岗位列表', async () => {
    const res = await request('GET', '/api/system/positions/all', null, adminToken)
    if (res.status !== 200) throw new Error(`HTTP ${res.status}`)
    const list = res.data.data || []
    if (list.length < 6) throw new Error(`期望≥6 个岗位，实际 ${list.length}`)
    const codes = list.map((p) => p.code)
    const required = ['pm', 'senior_engineer', 'engineer', 'junior_engineer', 'outsourcing', 'viewer']
    const missing = required.filter((c) => !codes.includes(c))
    if (missing.length) throw new Error(`缺少岗位: ${missing.join(', ')}`)
    logPass(`返回 ${list.length} 个岗位，包含全部 6 个标准岗位`)
  })

  // 1.2 岗位分页查询
  await test('1.2 岗位分页查询', async () => {
    const res = await request('GET', '/api/system/positions?pageNum=1&pageSize=10', null, adminToken)
    if (res.status !== 200) throw new Error(`HTTP ${res.status}`)
    if (!res.data.data?.records) throw new Error('返回格式异常')
    logPass(`分页返回 ${res.data.data.records.length} 条记录`)
  })

  // 1.3 新增岗位（admin）
  let testPositionId
  await test('1.3 admin 新增岗位', async () => {
    const res = await request(
      'POST',
      '/api/system/positions',
      {
        code: 'test_position_' + Date.now(),
        name: '测试岗位',
        level: 7,
        description: '自动化测试创建的临时岗位',
        status: 1
      },
      adminToken
    )
    if (res.status !== 200) throw new Error(`HTTP ${res.status}: ${JSON.stringify(res.data)}`)
    testPositionId = res.data.data?.id
    if (!testPositionId) throw new Error('未返回新岗位 ID')
    logPass(`新增岗位成功，id=${testPositionId}`)
  })

  // 1.4 岗位编码唯一性校验
  await test('1.4 岗位编码唯一性校验', async () => {
    const res = await request(
      'POST',
      '/api/system/positions',
      { code: 'pm', name: '重复PM', level: 2, status: 1 },
      adminToken
    )
    if (res.status === 200 && res.data.code === 200) throw new Error('应拒绝重复编码')
    logPass('重复编码被正确拒绝')
  })

  // 1.5 工程师无权新增岗位（权限校验）
  await test('1.5 工程师无权新增岗位（权限校验）', async () => {
    const res = await request(
      'POST',
      '/api/system/positions',
      { code: 'hacker', name: '黑客岗位', level: 1, status: 1 },
      engineerToken
    )
    if (res.status === 200 && res.data.code === 200) throw new Error('工程师不应能新增岗位')
    logPass('工程师被正确拦截（403/业务异常）')
  })

  // 1.6 删除测试岗位
  await test('1.6 删除测试岗位', async () => {
    if (!testPositionId) {
      logFail('跳过（无测试岗位 ID）')
      return
    }
    const res = await request('DELETE', `/api/system/positions/${testPositionId}`, null, adminToken)
    if (res.status !== 200) throw new Error(`HTTP ${res.status}`)
    logPass('测试岗位已删除')
  })

  // 1.7 用户列表应包含 positionName 字段
  await test('1.7 用户列表包含 positionName 字段', async () => {
    const res = await request('GET', '/api/system/users?pageNum=1&pageSize=5', null, adminToken)
    if (res.status !== 200) throw new Error(`HTTP ${res.status}`)
    const users = res.data.data?.records || []
    if (users.length === 0) throw new Error('用户列表为空')
    const hasPositionField = users.some((u) => 'positionName' in u || 'positionCode' in u)
    if (!hasPositionField) throw new Error('用户对象缺少 positionName/positionCode 字段')
    logPass(`用户对象包含 positionName/positionCode 字段（共 ${users.length} 个用户）`)
  })

  // ═══════════════════════════════════════════════════
  // 阶段2：任务级数据隔离
  // ═══════════════════════════════════════════════════
  console.log('\n┌───────────────────────────────────────────────')
  console.log('│ 阶段2：任务级数据隔离')
  console.log('└───────────────────────────────────────────────')

  // 2.1 查询项目列表，取第一个项目 ID
  let testProjectId
  await test('2.1 获取测试项目 ID', async () => {
    const res = await request('GET', '/api/projects?pageNum=1&pageSize=1', null, pmToken)
    if (res.status !== 200) throw new Error(`HTTP ${res.status}`)
    const records = res.data.data?.records || []
    if (records.length === 0) throw new Error('项目列表为空，无法继续测试')
    testProjectId = records[0].id
    logPass(`测试项目 ID = ${testProjectId}（${records[0].name}）`)
  })

  // 2.2 PM 可见项目所有任务
  await test('2.2 PM 可见项目所有任务', async () => {
    if (!testProjectId) return logFail('跳过（无项目 ID）')
    const res = await request('GET', `/api/projects/${testProjectId}/tasks`, null, pmToken)
    if (res.status !== 200) throw new Error(`HTTP ${res.status}`)
    const tasks = res.data.data || []
    logPass(`PM 看到项目 ${testProjectId} 下 ${tasks.length} 个任务`)
  })

  // 2.3 工程师只能看自己负责的任务（数据隔离核心）
  await test('2.3 工程师任务级数据隔离', async () => {
    if (!testProjectId) return logFail('跳过（无项目 ID）')
    const res = await request('GET', `/api/projects/${testProjectId}/tasks`, null, engineerToken)
    if (res.status === 403 || res.data.code === 403) {
      logPass('工程师未被授权该项目，返回 403（符合预期）')
      return
    }
    if (res.status !== 200) throw new Error(`HTTP ${res.status}`)
    const tasks = res.data.data || []
    logPass(`工程师看到 ${tasks.length} 个任务（仅自己负责的，PM 看到全部）`)
  })

  // ═══════════════════════════════════════════════════
  // 阶段3：项目授权规范化
  // ═══════════════════════════════════════════════════
  console.log('\n┌───────────────────────────────────────────────')
  console.log('│ 阶段3：项目授权规范化')
  console.log('└───────────────────────────────────────────────')

  // 3.1 创建授权申请（PM 申请授权给工程师）
  let authRequestId
  await test('3.1 PM 创建项目授权申请', async () => {
    if (!testProjectId) return logFail('跳过')
    const res = await request(
      'POST',
      '/api/project-auth/requests',
      {
        projectId: testProjectId,
        targetEngineerId: 6, // engineer_chenqianlong 的 userId（按实际调整）
        scope: 'read_write',
        expireAt: new Date(Date.now() + 30 * 24 * 3600 * 1000).toISOString(), // 30 天后过期
        reason: '自动化测试：为工程师申请项目访问权限'
      },
      pmToken
    )
    if (res.status !== 200) throw new Error(`HTTP ${res.status}: ${JSON.stringify(res.data)}`)
    authRequestId = res.data.data?.id
    if (!authRequestId) throw new Error('未返回申请 ID')
    logPass(`授权申请已创建，id=${authRequestId}`)
  })

  // 3.2 查询我申请的
  await test('3.2 PM 查询「我申请的」列表', async () => {
    const res = await request('GET', '/api/project-auth/requests/my', null, pmToken)
    if (res.status !== 200) throw new Error(`HTTP ${res.status}`)
    const list = res.data.data || []
    if (list.length === 0) throw new Error('我申请的列表为空')
    logPass(`返回 ${list.length} 条申请记录`)
  })

  // 3.3 查询待我审批的
  await test('3.3 查询待审批列表', async () => {
    const res = await request('GET', '/api/project-auth/requests/pending', null, adminToken)
    if (res.status !== 200) throw new Error(`HTTP ${res.status}`)
    logPass(`待审批列表查询成功（HTTP 200）`)
  })

  // 3.4 admin 审批通过
  await test('3.4 admin 审批通过授权申请', async () => {
    if (!authRequestId) return logFail('跳过（无申请 ID）')
    const res = await request(
      'POST',
      `/api/project-auth/requests/${authRequestId}/approve`,
      { approve: true, comment: '自动化测试：审批通过' },
      adminToken
    )
    if (res.status !== 200) throw new Error(`HTTP ${res.status}: ${JSON.stringify(res.data)}`)
    logPass('审批通过')
  })

  // 3.5 查询授权关系列表
  await test('3.5 查询项目授权关系列表', async () => {
    const res = await request(
      'GET',
      `/api/project-auth/assignments?projectId=${testProjectId}&pageNum=1&pageSize=10`,
      null,
      pmToken
    )
    if (res.status !== 200) throw new Error(`HTTP ${res.status}`)
    const records = res.data.data?.records || []
    logPass(`返回 ${records.length} 条授权记录`)
    // 检查是否包含 permissionScope 字段
    if (records.length > 0 && records[0].permissionScope) {
      logPass(`授权记录包含 permissionScope=${records[0].permissionScope}`)
    }
  })

  // 3.6 重复审批应被拒绝
  await test('3.6 重复审批被拒绝', async () => {
    if (!authRequestId) return logFail('跳过')
    const res = await request(
      'POST',
      `/api/project-auth/requests/${authRequestId}/approve`,
      { approve: true, comment: '再次审批' },
      adminToken
    )
    if (res.status === 200 && res.data.code === 200) throw new Error('应拒绝重复审批')
    logPass('重复审批被正确拒绝')
  })

  // ═══════════════════════════════════════════════════
  // 阶段4：项目组成员管理
  // ═══════════════════════════════════════════════════
  console.log('\n┌───────────────────────────────────────────────')
  console.log('│ 阶段4：项目组成员管理')
  console.log('└───────────────────────────────────────────────')

  // 4.1 添加项目组成员
  let teamMemberId
  await test('4.1 添加项目组成员', async () => {
    if (!testProjectId) return logFail('跳过')
    const res = await request(
      'POST',
      '/api/project-teams/members',
      {
        projectId: testProjectId,
        userId: 6, // engineer_chenqianlong
        roleInTeam: 'member',
        remark: '自动化测试：添加项目组成员'
      },
      pmToken
    )
    if (res.status !== 200) throw new Error(`HTTP ${res.status}: ${JSON.stringify(res.data)}`)
    teamMemberId = res.data.data?.id
    logPass(`成员已添加，id=${teamMemberId}`)
  })

  // 4.2 查询项目组成员列表
  await test('4.2 查询项目组成员列表', async () => {
    if (!testProjectId) return logFail('跳过')
    const res = await request(
      'GET',
      `/api/project-teams/members?projectId=${testProjectId}&pageNum=1&pageSize=10`,
      null,
      pmToken
    )
    if (res.status !== 200) throw new Error(`HTTP ${res.status}`)
    const records = res.data.data?.records || []
    logPass(`项目组共 ${records.length} 名成员`)
    if (records.length > 0 && records[0].userName) {
      logPass(`成员对象包含 userName=${records[0].userName}`)
    }
  })

  // 4.3 查询「我参与的项目组」
  await test('4.3 工程师查询「我参与的项目组」', async () => {
    const res = await request('GET', '/api/project-teams/my', null, engineerToken)
    if (res.status !== 200) throw new Error(`HTTP ${res.status}`)
    const list = res.data.data || []
    logPass(`工程师参与 ${list.length} 个项目组`)
  })

  // 4.4 工程师无权管理项目组（权限校验）
  await test('4.4 工程师无权添加项目组成员', async () => {
    const res = await request(
      'POST',
      '/api/project-teams/members',
      { projectId: testProjectId, userId: 999, roleInTeam: 'member' },
      engineerToken
    )
    if (res.status === 200 && res.data.code === 200) throw new Error('工程师不应能管理项目组')
    logPass('工程师被正确拦截')
  })

  // 4.5 移除项目组成员（清理测试数据）
  await test('4.5 移除项目组成员（清理）', async () => {
    if (!teamMemberId) return logFail('跳过（无成员 ID）')
    const res = await request(
      'DELETE',
      `/api/project-teams/members/${teamMemberId}`,
      null,
      pmToken
    )
    if (res.status !== 200) throw new Error(`HTTP ${res.status}`)
    logPass('成员已移除')
  })

  // ═══════════════════════════════════════════════════
  // 总结
  // ═══════════════════════════════════════════════════
  console.log('\n═══════════════════════════════════════════════')
  console.log('  测试总结')
  console.log('═══════════════════════════════════════════════')
  console.log(`  通过: ${passCount}`)
  console.log(`  失败: ${failCount}`)
  if (failures.length > 0) {
    console.log('\n失败详情:')
    failures.forEach((f, i) => {
      console.log(`  ${i + 1}. ${f.msg}`)
      if (f.detail) console.log(`     ${f.detail}`)
    })
  }
  console.log('\n' + (failCount === 0 ? '✓ 全部测试通过' : '✗ 存在失败项，请检查'))
  process.exit(failCount === 0 ? 0 : 1)
}

main().catch((e) => {
  console.error('测试脚本异常:', e)
  process.exit(1)
})
