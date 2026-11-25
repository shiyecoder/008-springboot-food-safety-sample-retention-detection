/* 设计稿外壳：按角色渲染侧边栏 + 顶栏，避免 11 个页面重复抄导航。
   页面上写 <div class="shell" data-shell="主管" data-active="/dashboard"></div> 即可。 */
(function () {
  const NAV = [
    { group: '总览', items: [['/dashboard', '食品安全看板', 'i-dashboard', ['留样员', '检测员', '主管', '管理员']]] },
    { group: '留样业务', items: [
      ['/tasks', '当日留样任务', 'i-task', ['留样员', '主管']],
      ['/samples', '留样台账', 'i-sample', ['留样员', '检测员', '主管']],
      ['/cabinet', '留样柜位图', 'i-cabinet', ['留样员', '主管']]
    ]},
    { group: '检测与处置', items: [
      ['/inspection', '检测登记', 'i-inspect', ['检测员', '主管']],
      ['/incident', '不合格事件', 'i-incident', ['检测员', '主管']],
      ['/expiry', '到期处置中心', 'i-disposal', ['留样员', '主管']]
    ]},
    { group: '配置', items: [
      ['/rule', '留样与校验规则', 'i-rule', ['主管', '管理员']],
      ['/base', '基础数据', 'i-base', ['主管', '管理员']]
    ]},
    { group: '系统', items: [['/system', '系统管理', 'i-system', ['管理员']]] }
  ]
  const USERS = {
    '留样员': ['liuyang01', '李秀兰', '后勤保障处 · 第二食堂'],
    '检测员': ['jiance01', '陈志远', '食品安全科 · 快检室'],
    '主管': ['zhuguan01', '王建国', '食品安全科'],
    '管理员': ['admin', '系统管理员', '后勤保障处 · 信息岗']
  }
  const ICON = (id, s = 15) => `<svg width="${s}" height="${s}" style="flex:0 0 ${s}px"><use href="../icons.svg#${id}"/></svg>`

  document.querySelectorAll('[data-shell]').forEach(el => {
    const role = el.dataset.shell
    const active = el.dataset.active || ''
    const [acct, name, org] = USERS[role] || USERS['主管']
    const nav = NAV.map(g => {
      const items = g.items.filter(it => it[3].includes(role))
      if (!items.length) return ''
      return `<div class="grp">${g.group}</div>` + items.map(([href, label, icon]) =>
        `<a href="${href.slice(1)}.html" class="${href === active ? 'on' : ''}">${ICON(icon)}<span>${label}</span></a>`).join('')
    }).join('')

    el.innerHTML = `
      <aside class="side">
        <div class="logo"><i></i><span>食品安全留样</span></div>
        <nav>${nav}</nav>
        <div class="collapse">« 收起菜单</div>
      </aside>
      <div class="main">
        <header class="top">
          <span class="muted">${role}工作台</span>
          <span class="weak">/</span><span style="color:var(--c-text)">${active ? active.slice(1) : ''}</span>
          <div class="right">
            <span class="bell">${ICON('i-bell', 17)}<em>4</em></span>
            <span class="org muted">${org}</span>
            <span class="user"><span class="av">${name.slice(0, 1)}</span>${name} ▾</span>
          </div>
        </header>
        <div class="content"></div>
      </div>`

    // 页面主体写在 <template data-body> 中，由外壳搬运到内容区
    const body = el.querySelector('template[data-body]')
    if (body) el.querySelector('.content').appendChild(body.content.cloneNode(true))
  })
})()
