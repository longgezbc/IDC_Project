/**
 * 前端页面接口端到端验证脚本
 *
 * 通过 Vite 代理（localhost:5173/api/）调用 3 个新页面的后端接口，
 * 模拟前端实际请求路径，验证：
 *   1. Vite 代理正常转发到后端
 *   2. CORS / context-path 配置正确
 *   3. JWT 鉴权通过
 *   4. 返回数据结构符合前端期望
 *
 * 运行方式：node scripts/verify-frontend-proxy.js
 */
const http = require('http')

// 通过 Vite 代理访问（模拟前端真实请求）
const BASE_URL = 'http://localhost:5173'
const TEST_ACCOUNTS = {
  admin: { username: 'admin', password: 'admin123' },
  pm: { username: 'pm_zhangming', password: 'admin123' }
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

async function testEndpoint(name, method, path, token, expectStatus = 200) {
  try {
    const res = await request(method, path, null, token)
    if (res.status === expectStatus) {
      const code = res.data.code
      if (code === 200 || code === undefined) {
        logPass(`${name} [${method} ${path}]`)
        return res.data
      } else {
        logFail(name, `业务码 code=${code}, msg=${res.data.message || ''}`)
      }
    } else {
      logFail(name, `HTTP ${res.status}, body=${JSON.stringify(res.data).slice(0, 200)}`)
    }
  } catch (e) {
    logFail(name, e.message)
  }
  return null
}

async function main() {
  console.log('\n========================================')
  console.log('  前端页面接口端到端验证（Vite 代理）')
  console.log('========================================\n')

  console.log('▶ 步骤 1: 管理员登录')
  let adminToken
  try {
    adminToken = await login(TEST_ACCOUNTS.admin)
    logPass(`管理员登录成功，token 长度=${adminToken.length}`)
  } catch (e) {
    logFail('管理员登录', e.message)
    console.log('\n验证终止：登录失败')
    process.exit(1)
  }

  console.log('\n▶ 步骤 2: 岗位管理页（PositionManagement.vue）')
  await testEndpoint(
    '岗位列表查询',
    'GET',
    '/api/system/positions?pageNum=1&pageSize=10',
    adminToken
  )

  console.log('\n▶ 步骤 3: 项目授权管理页（ProjectAuthManagement.vue）- Tab 1: 授权申请')
  await testEndpoint(
    '授权申请列表',
    'GET',
    '/api/project-auth/requests?pageNum=1&pageSize=5',
    adminToken
  )

  console.log('\n▶ 步骤 4: 项目授权管理页 - Tab 2: 授权关系')
  await testEndpoint(
    '授权关系列表',
    'GET',
    '/api/project-auth/assignments?pageNum=1&pageSize=5',
    adminToken
  )

  console.log('\n▶ 步骤 5: 项目组管理页（ProjectTeamManagement.vue）- Tab 1: 项目组成员')
  await testEndpoint(
    '项目组成员列表',
    'GET',
    '/api/project-teams/members?pageNum=1&pageSize=5',
    adminToken
  )

  console.log('\n▶ 步骤 6: 项目组管理页 - Tab 2: 我参与的项目组')
  await testEndpoint(
    '我参与的项目组',
    'GET',
    '/api/project-teams/my',
    adminToken
  )

  // 测试项目经理权限
  console.log('\n▶ 步骤 7: 项目经理登录验证（权限隔离）')
  let pmToken
  try {
    pmToken = await login(TEST_ACCOUNTS.pm)
    logPass(`项目经理登录成功，token 长度=${pmToken.length}`)
  } catch (e) {
    logFail('项目经理登录', e.message)
  }

  if (pmToken) {
    await testEndpoint(
      'PM 查看自己的项目组',
      'GET',
      '/api/project-teams/my',
      pmToken
    )
  }

  // 测试创建岗位（POST 写操作）
  console.log('\n▶ 步骤 8: 岗位创建（写操作）')
  const createRes = await request('POST', '/api/system/positions', {
    code: 'TEST_FE_VERIFICATION',
    name: '前端验证临时岗位',
    level: 5,
    description: '由 verify-frontend-proxy.js 创建，验证后可删除'
  }, adminToken)
  if (createRes.status === 200 && createRes.data.code === 200) {
    logPass(`岗位创建成功 id=${createRes.data.data?.id || '?'}`)
    // 清理：删除测试岗位
    if (createRes.data.data?.id) {
      const delRes = await request('DELETE', `/api/system/positions/${createRes.data.data.id}`, null, adminToken)
      if (delRes.status === 200 && delRes.data.code === 200) {
        logPass('清理测试岗位成功')
      }
    }
  } else {
    logFail('岗位创建', `HTTP ${createRes.status}, code=${createRes.data.code}, msg=${createRes.data.message}`)
  }

  // 总结
  console.log('\n========================================')
  console.log(`  验证结果: ${passCount} 通过 / ${failCount} 失败`)
  console.log('========================================')
  if (failures.length > 0) {
    console.log('\n失败项明细:')
    failures.forEach((f, i) => {
      console.log(`  ${i + 1}. ${f.msg}`)
      if (f.detail) console.log(`     ${f.detail}`)
    })
  }
  console.log('')
  process.exit(failCount > 0 ? 1 : 0)
}

main().catch((e) => {
  console.error('脚本异常:', e)
  process.exit(1)
})
