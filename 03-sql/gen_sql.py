#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成 dash 食品安全留样库的初始化脚本 sql/init.sql。
数据分布刻意打散：创建时间跨最近 6 个月，状态覆盖全部节点，图片按 ((i*7)%N)+1 轮换避免相邻重复。
"""
import datetime as dt
import pathlib

OUT = pathlib.Path(__file__).resolve().parent.parent / "sql" / "init.sql"
PWD = "$2y$10$6lAyH889uIVFaL5Oc.ZtaePVvRv/krLbDeKP0vrqpVEyVyTXjwX0m"      # 123456
ADMIN_PWD = "$2y$10$UYm09re9gTaWcY.5oD.9Gum6NYKztmpJR8/0r3.xh.kgqZWBgG9ge"  # admin123

def q(v):
    if v is None:
        return "NULL"
    if isinstance(v, (int, float)):
        return str(v)
    return "'" + str(v).replace("\\", "\\\\").replace("'", "''") + "'"

def rows(table, cols, data):
    out = [f"INSERT INTO `{table}` ({', '.join('`'+c+'`' for c in cols)}) VALUES"]
    vals = []
    for r in data:
        vals.append("(" + ", ".join(q(x) for x in r) + ")")
    return out[0] + "\n" + ",\n".join(vals) + ";"

S = dt.datetime(2026, 5, 1, 8, 0, 0)
SAMPLERS = [(1, "李秀兰"), (2, "周慧敏"), (4, "何芳"), (10, "郑小红")]

def t(days=0, hh=8, mm=0, ss=0):
    return (dt.datetime(2026, 10, 31, hh, mm, ss) + dt.timedelta(days=days)).strftime("%Y-%m-%d %H:%M:%S")

sql = []
A = sql.append

A("""-- =====================================================================
-- 食品安全留样与检测管理系统 · 数据库初始化脚本
-- 库名 new_008，字符集 utf8mb4
-- 说明：所有图片字段均指向项目内真实本地文件 /uploads/seed/...，
--       不含任何外链、占位图或空串；重新导入即可完整复现。
-- =====================================================================
DROP DATABASE IF EXISTS `new_008`;
CREATE DATABASE `new_008` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `new_008`;
SET NAMES utf8mb4;
""")

# ---------------- 组织 / 角色 / 用户 ----------------
A("""-- ---------- 组织 ----------
CREATE TABLE `sys_org` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(64) NOT NULL COMMENT '组织名称',
  `parent_id` bigint NOT NULL DEFAULT 0 COMMENT '上级组织',
  `org_type` varchar(16) NOT NULL DEFAULT 'DEPT' COMMENT '类型 DEPT科室 CANTEEN食堂',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组织';""")

A(rows("sys_org", ["id","name","parent_id","org_type","sort","create_time"], [
    (1,"后勤保障处",0,"DEPT",1,t(-180)),
    (2,"食品安全科",1,"DEPT",2,t(-180)),
    (3,"第一食堂",1,"CANTEEN",3,t(-180)),
    (4,"第二食堂",1,"CANTEEN",4,t(-180)),
    (5,"快检室",2,"DEPT",5,t(-170)),
]))

A("""-- ---------- 角色 ----------
CREATE TABLE `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(32) NOT NULL COMMENT '角色编码',
  `name` varchar(32) NOT NULL COMMENT '角色名称',
  `data_scope` varchar(16) NOT NULL DEFAULT 'SELF' COMMENT '数据范围 ALL/DEPT/SELF',
  `remark` varchar(200) DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';""")
A(rows("sys_role", ["id","code","name","data_scope","remark","deleted","create_time","update_time"], [
    (1,"SAMPLER","留样员","SELF","接收任务、录入样品重量与仓位",0,t(-180),t(-180)),
    (2,"INSPECTOR","检测员","ALL","跨食堂抽检，登记检测项目与结果",0,t(-180),t(-180)),
    (3,"SUPERVISOR","食品安全主管","ALL","维护标准、复核留样、处置不合格、审批销毁",0,t(-180),t(-180)),
    (4,"ADMIN","系统管理员","ALL","组织、仓位、用户、角色、字典与日志",0,t(-180),t(-180)),
]))

A("""-- ---------- 用户 ----------
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(32) NOT NULL COMMENT '登录名',
  `password` varchar(100) NOT NULL COMMENT 'BCrypt 密文，任何接口不返回',
  `real_name` varchar(32) NOT NULL COMMENT '姓名',
  `avatar` varchar(200) DEFAULT NULL COMMENT '头像相对路径',
  `org_id` bigint DEFAULT NULL COMMENT '所属组织',
  `role_id` bigint NOT NULL COMMENT '角色',
  `phone` varchar(20) DEFAULT NULL,
  `email` varchar(64) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  `fail_count` int NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
  `lock_until` datetime DEFAULT NULL COMMENT '锁定截止时间',
  `last_login_time` datetime DEFAULT NULL,
  `last_login_ip` varchar(40) DEFAULT NULL,
  `remark` varchar(200) DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_name` (`username`),
  KEY `idx_user_org` (`org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';""")

users = [
    ("liuyang01","李秀兰",1,3,1,"13800002210","lixiulan@canteen.edu.cn","负责第二食堂午餐、晚餐留样"),
    ("liuyang02","周慧敏",2,3,1,"13900007734","zhouhuimin@canteen.edu.cn","负责第一食堂留样"),
    ("liuyang03","孙立",3,3,1,"13500003321","sunli@canteen.edu.cn","第一食堂留样（账号锁定）"),
    ("liuyang04","何芳",4,4,1,"13600004412","hefang@canteen.edu.cn","第二食堂留样"),
    ("jiance01","陈志远",5,2,1,"13700005512","chenzhiyuan@canteen.edu.cn","快检室，负责微生物与添加剂检测"),
    ("jiance02","吴倩",6,2,1,"13700005513","wuqian@canteen.edu.cn","快检室，负责感官与综合检测"),
    ("zhuguan01","王建国",7,2,1,"13600008890","wangjianguo@canteen.edu.cn","食品安全科科长"),
    ("zhuguan02","刘敏",8,2,1,"13600008891","liumin@canteen.edu.cn","食品安全科副科长"),
    ("admin","系统管理员",9,1,1,"13000000000","admin@canteen.edu.cn","超级管理员"),
    ("liuyang05","郑小红",10,4,1,"13800002211","zhengxiaohong@canteen.edu.cn","第二食堂留样"),
    ("jiance03","马涛",11,5,1,"13700005514","matao@canteen.edu.cn","快检室"),
    ("zhuguan03","杨静",12,2,1,"13600008892","yangjing@canteen.edu.cn","食品安全科"),
]
urows = []
AVATAR_OF_USER = {1: 1, 2: 2, 3: 4, 4: 3, 5: 6, 6: 5, 7: 7, 8: 8, 9: 7, 10: 2, 11: 6, 12: 8}
for i, (un, rn, _, org, _, ph, em, rm) in enumerate(users, 1):
    av = f"/uploads/seed/avatar/{AVATAR_OF_USER[i]:02d}.jpg"
    pwd = ADMIN_PWD if un == "admin" else PWD
    lock = None
    if un == "liuyang03":
        lock = t(1, 9, 30)
    urows.append((i, un, pwd, rn, av, org, 1 if i in (1,2,3,4,10) else (2 if i in (5,6,11) else (3 if i in (7,8,12) else 4)),
                  ph, em, 1, 0, lock, t(-1, 8 + i % 8, 12 + i), f"10.12.33.{100+i}", rm, 0, 0, t(-150+i), t(-2, 9, 0)))
A(rows("sys_user", ["id","username","password","real_name","avatar","org_id","role_id","phone","email",
                    "status","fail_count","lock_until","last_login_time","last_login_ip","remark","deleted",
                    "version","create_time","update_time"], urows))

A("""-- ---------- 菜单 ----------
CREATE TABLE `sys_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint NOT NULL DEFAULT 0,
  `name` varchar(32) NOT NULL,
  `path` varchar(64) DEFAULT NULL COMMENT '前端路由',
  `perm` varchar(64) DEFAULT NULL COMMENT '按钮/接口权限标识',
  `icon` varchar(32) DEFAULT NULL,
  `menu_type` varchar(8) NOT NULL DEFAULT 'MENU' COMMENT 'GROUP/MENU/BUTTON',
  `sort` int NOT NULL DEFAULT 0,
  `visible` tinyint NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单（系统内不暴露菜单管理入口）';""")

menus = [
    (1,0,"总览",None,None,"i-dashboard","GROUP",1),
    (2,1,"食品安全看板","/dashboard",None,"i-dashboard","MENU",1),
    (3,0,"留样业务",None,None,"i-sample","GROUP",2),
    (4,3,"当日留样任务","/tasks",None,"i-task","MENU",1),
    (5,3,"留样台账","/samples",None,"i-sample","MENU",2),
    (6,3,"留样柜位图","/cabinet",None,"i-cabinet","MENU",3),
    (7,0,"检测与处置",None,None,"i-inspect","GROUP",3),
    (8,7,"检测登记","/inspection",None,"i-inspect","MENU",1),
    (9,7,"不合格事件","/incident",None,"i-incident","MENU",2),
    (10,7,"到期处置中心","/expiry",None,"i-disposal","MENU",3),
    (11,0,"配置",None,None,"i-rule","GROUP",4),
    (12,11,"留样与校验规则","/rule",None,"i-rule","MENU",1),
    (13,11,"基础数据","/base",None,"i-base","MENU",2),
    (14,0,"系统",None,None,"i-system","GROUP",5),
    (15,14,"系统管理","/system",None,"i-system","MENU",1),
    (16,0,"个人中心","/profile",None,"i-user","MENU",9),
]
A(rows("sys_menu", ["id","parent_id","name","path","perm","icon","menu_type","sort","visible"],
       [(a,b,c,d,e,f,g,h,1) for (a,b,c,d,e,f,g,h) in menus]))

A("""-- ---------- 角色菜单 ----------
CREATE TABLE `sys_role_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL,
  `menu_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_menu` (`role_id`,`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单';""")

role_menus = {
    1: [1,2,3,4,5,6,10,16],          # 留样员
    2: [1,2,3,5,7,8,9,11,13,16],     # 检测员
    3: [1,2,3,4,5,6,7,8,9,10,11,12,13,16],  # 主管
    4: [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16],  # 管理员
}
rm = []
rid = 1
for role, mids in role_menus.items():
    for m in mids:
        rm.append((rid, role, m)); rid += 1
A(rows("sys_role_menu", ["id","role_id","menu_id"], rm))

# ---------------- 基础数据 ----------------
A("""-- ---------- 食品分类 ----------
CREATE TABLE `food_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(32) NOT NULL COMMENT '分类编码',
  `name` varchar(32) NOT NULL COMMENT '分类名称',
  `min_weight` decimal(10,1) NOT NULL COMMENT '最低留样重量 g',
  `storage_cond` varchar(64) NOT NULL COMMENT '存储条件',
  `retain_hours` int NOT NULL COMMENT '保留时长 小时',
  `meals` varchar(32) NOT NULL COMMENT '适用餐次',
  `owner` varchar(32) DEFAULT NULL COMMENT '标准维护人',
  `status` tinyint NOT NULL DEFAULT 1,
  `sort` int NOT NULL DEFAULT 0,
  `deleted` tinyint NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cat_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='食品分类与留样标准';""")
cats = [
    ("C-HOT","热菜",125.0,"0–4 ℃ 冷藏",48,"早/午/晚","王建国",1,1),
    ("C-COLD","凉菜",125.0,"0–4 ℃ 冷藏",48,"午/晚","王建国",1,2),
    ("C-PASTRY","面点",100.0,"0–4 ℃ 冷藏",48,"早/午","李秀兰",1,3),
    ("C-SOUP","汤品",250.0,"0–4 ℃ 冷藏",48,"午/晚","李秀兰",1,4),
    ("C-DRINK","自制饮品",200.0,"0–4 ℃ 冷藏",24,"全天","陈志远",0,5),
    ("C-STAPLE","主食",150.0,"0–4 ℃ 冷藏",48,"早/午/晚","周慧敏",1,6),
    ("C-PASTRY2","西点",100.0,"0–4 ℃ 冷藏",24,"早/午","周慧敏",1,7),
    ("C-SALAD","沙拉",150.0,"0–4 ℃ 冷藏",24,"午/晚","刘敏",0,8),
]
A(rows("food_category", ["id","code","name","min_weight","storage_cond","retain_hours","meals","owner","status","sort","deleted","create_time","update_time"],
       [(i, c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8], 0, t(-160+i), t(-10, 10, i))
        for i, c in enumerate(cats, 1)]))

A("""-- ---------- 供应单位 ----------
CREATE TABLE `supplier` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `contact` varchar(32) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `license_no` varchar(64) DEFAULT NULL COMMENT '食品经营许可证号',
  `category` varchar(32) DEFAULT NULL COMMENT '供货类别',
  `status` tinyint NOT NULL DEFAULT 1,
  `deleted` tinyint NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应单位';""")
A(rows("supplier", ["id","name","contact","phone","license_no","category","status","deleted","create_time","update_time"], [
    (1,"绿源蔬菜配送中心","张海","021-66881234","JY13101010123456","蔬菜",1,0,t(-150),t(-20)),
    (2,"正大肉食品有限公司","李强","021-66885678","JY13101010654321","肉类",1,0,t(-150),t(-18)),
    (3,"东海水产批发部","王涛","021-66889012","JY13101010987654","水产",1,0,t(-140),t(-30)),
    (4,"金穗粮油贸易公司","赵敏","021-66884567","JY13101010456789","粮油",1,0,t(-140),t(-25)),
    (5,"康达乳品配送","孙丽","021-66887890","JY13101010789012","乳品",1,0,t(-130),t(-15)),
    (6,"味美调味品商行","周杰","021-66883210","JY13101010321654","调味品",1,0,t(-120),t(-40)),
]))

A("""-- ---------- 加工档口 ----------
CREATE TABLE `workshop` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `org_id` bigint NOT NULL COMMENT '所属食堂',
  `manager` varchar(32) DEFAULT NULL COMMENT '档口负责人',
  `phone` varchar(20) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT 1,
  `deleted` tinyint NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='加工档口';""")
A(rows("workshop", ["id","name","org_id","manager","phone","status","deleted","create_time","update_time"], [
    (1,"一食堂热菜档",3,"张师傅","13811110001",1,0,t(-150),t(-10)),
    (2,"一食堂素菜档",3,"刘师傅","13811110002",1,0,t(-150),t(-10)),
    (3,"二食堂热菜档",4,"陈师傅","13811110003",1,0,t(-150),t(-12)),
    (4,"二食堂素菜档",4,"黄师傅","13811110004",1,0,t(-150),t(-12)),
    (5,"面点档",3,"赵师傅","13811110005",1,0,t(-140),t(-9)),
    (6,"凉菜间",4,"钱师傅","13811110006",1,0,t(-140),t(-9)),
    (7,"汤品档",4,"孙师傅","13811110007",1,0,t(-140),t(-8)),
    (8,"教职工餐厅",4,"吴师傅","13811110008",1,0,t(-130),t(-8)),
    (9,"清真窗口",3,"马师傅","13811110009",1,0,t(-120),t(-7)),
]))

# 仓位：3 个冷藏柜 × 24 位
locs = []
lid = 1
for ci, cab in enumerate(["A", "B", "C"]):
    for layer in (1, 2, 3):
        for slot in range(1, 9):
            code = f"{cab}-{layer}-{slot:02d}"
            locs.append((lid, cab, layer, slot, code,
                         ["一食堂备餐间", "二食堂备餐间", "快检室"][ci], 0.0, 4.0, 1, 0, t(-150), t(-5)))
            lid += 1
A("""-- ---------- 仓位 ----------
CREATE TABLE `storage_location` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `cabinet_no` varchar(16) NOT NULL COMMENT '冷藏柜编号',
  `layer_no` int NOT NULL COMMENT '层',
  `slot_no` int NOT NULL COMMENT '位',
  `code` varchar(32) NOT NULL COMMENT '仓位编码 如 A-3-02',
  `location_desc` varchar(64) DEFAULT NULL COMMENT '摆放位置说明',
  `temp_min` decimal(5,1) NOT NULL DEFAULT 0.0,
  `temp_max` decimal(5,1) NOT NULL DEFAULT 4.0,
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1可用 0停用',
  `occupied` tinyint NOT NULL DEFAULT 0 COMMENT '是否被占用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_loc_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='留样仓位';""")
A(rows("storage_location", ["id","cabinet_no","layer_no","slot_no","code","location_desc","temp_min","temp_max","status","occupied","create_time","update_time"], locs))

A("""-- ---------- 生产批次 ----------
CREATE TABLE `food_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_no` varchar(40) NOT NULL COMMENT '批次号',
  `name` varchar(64) NOT NULL COMMENT '批次名称',
  `meal` varchar(16) NOT NULL COMMENT '餐次',
  `category_id` bigint DEFAULT NULL,
  `workshop_id` bigint DEFAULT NULL,
  `supplier_id` bigint DEFAULT NULL,
  `produce_time` datetime NOT NULL COMMENT '加工时间',
  `manager` varchar(32) DEFAULT NULL COMMENT '负责人',
  `menu_code` varchar(40) DEFAULT NULL COMMENT '当日菜单编号',
  `dish_count` int NOT NULL DEFAULT 0 COMMENT '菜品数',
  `status` varchar(16) NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING进行中 FINISHED已完结',
  `remark` varchar(200) DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_no` (`batch_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='食品生产批次';""")

batches = [
    ("PC-20261031-B01","10月31日早餐批次","早餐",5,5,4,"2026-10-31 05:40:00","赵师傅","MU-20261031-B",5,"RUNNING"),
    ("PC-20261031-L01","10月31日午餐一批次","午餐",1,1,2,"2026-10-31 09:30:00","张师傅","MU-20261031-L",8,"RUNNING"),
    ("PC-20261031-L02","10月31日午餐二批次","午餐",3,3,2,"2026-10-31 09:40:00","陈师傅","MU-20261031-L",6,"RUNNING"),
    ("PC-20261031-L03","10月31日午餐汤品","午餐",4,7,4,"2026-10-31 10:10:00","孙师傅","MU-20261031-L",3,"RUNNING"),
    ("PC-20261030-L01","10月30日午餐一批次","午餐",1,1,2,"2026-10-30 09:25:00","张师傅","MU-20261030-L",8,"FINISHED"),
    ("PC-20261030-L02","10月30日午餐二批次","午餐",3,3,2,"2026-10-30 09:35:00","陈师傅","MU-20261030-L",6,"FINISHED"),
    ("PC-20261030-B01","10月30日早餐批次","早餐",5,5,4,"2026-10-30 05:35:00","赵师傅","MU-20261030-B",5,"FINISHED"),
    ("PC-20261029-L01","10月29日午餐一批次","午餐",1,1,2,"2026-10-29 09:20:00","张师傅","MU-20261029-L",8,"FINISHED"),
    ("PC-20261029-L02","10月29日午餐二批次","午餐",3,3,2,"2026-10-29 09:40:00","陈师傅","MU-20261029-L",6,"FINISHED"),
    ("PC-20261028-L01","10月28日午餐批次","午餐",3,3,3,"2026-10-28 09:30:00","陈师傅","MU-20261028-L",7,"FINISHED"),
    ("PC-20261027-B01","10月27日早餐批次","早餐",5,5,4,"2026-10-27 05:30:00","赵师傅","MU-20261027-B",5,"FINISHED"),
    ("PC-20261026-L01","10月26日午餐批次","午餐",1,1,2,"2026-10-26 09:35:00","张师傅","MU-20261026-L",8,"FINISHED"),
    ("PC-20261024-B01","10月24日早餐批次","早餐",5,5,4,"2026-10-24 05:25:00","赵师傅","MU-20261024-B",5,"FINISHED"),
    ("PC-20261018-L01","10月18日午餐批次","午餐",1,1,2,"2026-10-18 09:30:00","刘师傅","MU-20261018-L",7,"FINISHED"),
    ("PC-20261007-L01","10月07日午餐批次","午餐",4,7,4,"2026-10-07 09:40:00","孙师傅","MU-20261007-L",5,"FINISHED"),
    ("PC-20260915-L01","09月15日午餐批次","午餐",1,1,2,"2026-09-15 09:30:00","张师傅","MU-20260915-L",8,"FINISHED"),
    ("PC-20260902-B01","09月02日早餐批次","早餐",5,5,4,"2026-09-02 05:30:00","赵师傅","MU-20260902-B",5,"FINISHED"),
    ("PC-20260812-L01","08月12日午餐批次","午餐",3,3,2,"2026-08-12 09:35:00","陈师傅","MU-20260812-L",6,"FINISHED"),
    ("PC-20260718-L01","07月18日午餐批次","午餐",1,1,2,"2026-07-18 09:30:00","张师傅","MU-20260718-L",7,"FINISHED"),
    ("PC-20260620-L01","06月20日午餐批次","午餐",4,7,4,"2026-06-20 09:40:00","孙师傅","MU-20260620-L",5,"FINISHED"),
    ("PC-20260522-B01","05月22日早餐批次","早餐",5,5,4,"2026-05-22 05:30:00","赵师傅","MU-20260522-B",5,"FINISHED"),
    ("PC-20260508-L01","05月08日午餐批次","午餐",3,3,3,"2026-05-08 09:30:00","陈师傅","MU-20260508-L",6,"FINISHED"),
    ("PC-20260925-L01","09月25日午餐批次","午餐",1,1,2,"2026-09-25 09:30:00","张师傅","MU-20260925-L",7,"FINISHED"),
    ("PC-20260705-B01","07月05日早餐批次","早餐",5,5,4,"2026-07-05 05:30:00","赵师傅","MU-20260705-B",5,"FINISHED"),
]
A(rows("food_batch", ["id","batch_no","name","meal","category_id","workshop_id","supplier_id","produce_time","manager","menu_code","dish_count","status","remark","deleted","create_time","update_time"],
       [(i, b[0], b[1], b[2], b[3], b[4], b[5], b[6], b[7], b[8], b[9], b[10], None, 0, b[6], b[6]) for i, b in enumerate(batches, 1)]))

A("""-- ---------- 留样规则 ----------
CREATE TABLE `sample_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category_id` bigint NOT NULL COMMENT '食品分类',
  `meal` varchar(16) NOT NULL COMMENT '适用餐次',
  `min_weight` decimal(10,1) NOT NULL COMMENT '最低重量 g',
  `storage_cond` varchar(64) NOT NULL COMMENT '存储条件',
  `retain_hours` int NOT NULL COMMENT '保留时长',
  `version` varchar(16) NOT NULL COMMENT '规则版本',
  `effective_time` datetime NOT NULL COMMENT '生效时间',
  `status` tinyint NOT NULL DEFAULT 1,
  `remark` varchar(200) DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_rule_cat` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='留样规则';""")
A(rows("sample_rule", ["id","category_id","meal","min_weight","storage_cond","retain_hours","version","effective_time","status","remark","deleted","create_time","update_time"], [
    (1,1,"早/午/晚",125.0,"0–4 ℃ 冷藏",48,"v3","2026-09-01 00:00:00",1,"热菜通用规则",0,t(-60),t(-60)),
    (2,2,"午/晚",125.0,"0–4 ℃ 冷藏",48,"v3","2026-09-01 00:00:00",1,"凉菜通用规则",0,t(-60),t(-60)),
    (3,3,"早/午",100.0,"0–4 ℃ 冷藏",48,"v3","2026-09-01 00:00:00",1,"面点规则",0,t(-60),t(-60)),
    (4,4,"午/晚",250.0,"0–4 ℃ 冷藏",48,"v3","2026-09-01 00:00:00",1,"汤品规则",0,t(-60),t(-60)),
    (5,6,"早/午/晚",150.0,"0–4 ℃ 冷藏",48,"v3","2026-09-01 00:00:00",1,"主食规则",0,t(-60),t(-60)),
    (6,7,"早/午",100.0,"0–4 ℃ 冷藏",24,"v3","2026-09-01 00:00:00",1,"西点规则",0,t(-60),t(-60)),
]))

# ---------------- 留样主档 ----------------
# (food, cat, meal, batch_idx, weight, status, offset_days, hh, mm, store_hours)
DISHES = [
    ("红烧狮子头",1,"午餐",2,132.5),("清蒸鲈鱼",1,"午餐",2,138.0),("糖醋里脊",1,"午餐",3,141.2),
    ("香菇滑鸡",1,"午餐",3,129.4),("清炒时蔬",2,"午餐",3,127.6),("凉拌木耳",2,"午餐",3,108.0),
    ("紫菜蛋花汤",4,"午餐",4,268.0),("鲜肉包子",3,"早餐",1,112.6),("小米粥",6,"早餐",1,255.0),
    ("番茄炒蛋",1,"午餐",5,131.0),("青椒肉丝",1,"午餐",5,134.8),("干煸豆角",2,"午餐",5,126.2),
    ("土豆炖牛肉",1,"午餐",6,142.5),("冬瓜丸子汤",4,"午餐",6,262.4),("蒜泥白肉",2,"午餐",6,128.9),
    ("水煮鱼片",1,"午餐",8,145.6),("蒜蓉西兰花",2,"午餐",8,130.2),("红烧排骨",1,"午餐",9,139.8),
    ("凉拌黄瓜",2,"午餐",9,126.8),("三鲜豆腐",1,"午餐",10,133.4),("银耳莲子羹",4,"午餐",11,258.6),
    ("葱油饼",3,"早餐",11,104.5),("白灼虾",1,"午餐",12,136.9),("清炒油麦菜",2,"午餐",12,125.4),
    ("梅菜扣肉",1,"午餐",13,148.2),("南瓜粥",6,"早餐",13,252.8),("酸辣土豆丝",2,"午餐",14,127.1),
    ("咖喱鸡块",1,"午餐",15,137.5),("蒸水蛋",1,"午餐",16,130.6),("八宝粥",6,"早餐",16,260.2),
    ("红烧带鱼",1,"午餐",17,143.7),("手撕包菜",2,"午餐",18,126.5),("香菇青菜",2,"午餐",19,128.3),
    ("红烧肉",1,"午餐",20,151.4),("绿豆汤",4,"午餐",21,255.9),("花卷",3,"早餐",22,101.8),
    # 近 7 日历史记录（补足看板趋势与台账分页的体量）
    ("红烧鸡腿",1,"午餐",7,144.2),("清炒菠菜",2,"午餐",7,127.8),("西红柿鸡蛋汤",4,"午餐",7,256.4),("豆沙包",3,"早餐",7,103.6),
    ("宫保鸡丁",1,"午餐",8,136.4),("凉拌海带丝",2,"午餐",8,126.0),("紫菜汤",4,"午餐",8,254.8),("燕麦粥",6,"早餐",8,251.5),
    ("鱼香肉丝",1,"午餐",9,138.6),("炝炒圆白菜",2,"午餐",9,127.2),("排骨冬瓜汤",4,"午餐",9,265.4),("小笼包",3,"早餐",9,105.4),
    ("肉末茄子",1,"午餐",10,134.2),("拍黄瓜",2,"午餐",10,125.8),("玉米粥",6,"早餐",10,252.4),("奶黄包",3,"早餐",10,102.8),
    ("香煎鸡排",1,"午餐",11,142.8),("蒜蓉油麦菜",2,"午餐",11,126.6),("罗宋汤",4,"午餐",11,262.2),("蛋炒饭",5,"午餐",11,280.5),
    ("回锅肉",1,"午餐",12,146.2),("凉拌豆皮",2,"午餐",12,126.8),("南瓜羹",4,"午餐",12,257.6),("葱香花卷",3,"早餐",12,104.4),
    ("红烧牛肉",1,"午餐",13,148.6),("清炒莴笋",2,"午餐",13,127.4),("山药排骨汤",4,"午餐",13,268.8),("麻酱烧饼",3,"早餐",13,104.2),
]
SAMPLES = []
for i, (food, cat, meal, bi, w) in enumerate(DISHES, 1):
    SAMPLES.append((i, food, cat, meal, bi, w))

# 状态分配：前 4 待复核，5-18 保留中，19-24 待销毁，25-30 已销毁，31-36 已驳回
# 37 之后为近 7 日历史数据：每天 4 条，按天递推为 保留中/待销毁/已销毁
def status_of(i):
    if i <= 4: return "PENDING_REVIEW"
    if i <= 18: return "RETAINING"
    if i <= 24: return "PENDING_DISPOSE"
    if i <= 30: return "DISPOSED"
    if i <= 36: return "REJECTED"
    if i <= 44: return "RETAINING"
    if i <= 52: return "PENDING_DISPOSE"
    return "DISPOSED"

# 日期偏移：越靠后越早
def day_offset(i):
    if i <= 10: return 0
    if i <= 18: return -(1 + i % 4)
    if i <= 24: return -(8 + i % 4)
    if i <= 36: return -(20 + (i % 40))
    return -(1 + (i - 37) // 4)

locs_by_id = {l[0]: l[4] for l in locs}
sam = []
rec = []
att = []
for i, food, cat, meal, bi, w in SAMPLES:
    st = status_of(i)
    day = day_offset(i)
    hh = 12 if meal == "午餐" else 7
    mm = (i * 7) % 55
    store_t = t(day, hh, mm)
    retain = 48 if cat != 7 else 24
    expire = (dt.datetime.strptime(store_t, "%Y-%m-%d %H:%M:%S") + dt.timedelta(hours=retain)).strftime("%Y-%m-%d %H:%M:%S")
    lid = ((i * 7) % 72) + 1
    photo = f"/uploads/seed/dish/{((i*7)%12)+1:02d}.jpg"
    proof = f"/uploads/seed/proof/{((i*5)%6)+1:02d}.jpg"
    sampler = SAMPLERS[i % 4][0]
    sampler_name = dict(SAMPLERS)[sampler]
    reviewer = None
    review_time = None
    review_remark = None
    if st in ("RETAINING", "PENDING_DISPOSE", "DISPOSED"):
        reviewer, review_time = 7, t(day, hh + 1, 15)
        review_remark = "重量、密封、标识、仓位四项均符合要求，准予入柜保留"
    if st == "REJECTED":
        reviewer, review_time = 8, t(day, hh + 1, 10)
        review_remark = "实际重量低于标准，容器密封不严，需重新留样"
    inspect = {True: "PASSED", False: "NONE"}
    res = "PASSED"
    if i in (6, 18, 27, 33):
        res = "FAILED"
    elif i in (8, 11, 30):
        res = "RECHECK"
    elif st in ("PENDING_REVIEW", "REJECTED"):
        res = "NONE"
    elif i % 5 == 0:
        res = "NONE"
    locked = 1 if i in (6, 18, 27) else 0
    sam.append((i, f"LY{store_t[:10].replace('-','')}-{i:04d}", food, cat, meal,
                batches[bi - 1][0], bi, i, w, f"BX-{['A','B','C'][i%3]}-{1000+i:04d}",
                lid, locs_by_id[lid], round(2.0 + (i % 20) / 10.0, 1),
                store_t, expire, retain, photo, proof, sampler, sampler_name,
                reviewer, review_time, review_remark, st, res, locked,
                0 if st == "DISPOSED" else 0,
                "现场按标准分装，容器已封口" if i % 3 else "出餐温度偏高，已加强冷却",
                0, 0, t(day, hh - 1, 30), t(day, hh, mm)))

A("""-- ---------- 食品留样主档 ----------
CREATE TABLE `food_sample` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `sample_no` varchar(50) NOT NULL COMMENT '留样编号，业务唯一',
  `food_name` varchar(64) NOT NULL COMMENT '食品名称',
  `category_id` bigint NOT NULL COMMENT '食品分类',
  `meal` varchar(16) NOT NULL COMMENT '餐次',
  `batch_no` varchar(40) NOT NULL COMMENT '生产批次',
  `batch_id` bigint DEFAULT NULL,
  `task_id` bigint DEFAULT NULL COMMENT '来源任务',
  `weight` decimal(10,1) NOT NULL COMMENT '留样重量 g',
  `container_no` varchar(32) DEFAULT NULL COMMENT '容器编号',
  `location_id` bigint DEFAULT NULL COMMENT '仓位',
  `location_code` varchar(32) DEFAULT NULL COMMENT '仓位编码冗余，便于列表展示',
  `temperature` decimal(5,1) DEFAULT NULL COMMENT '入柜温度 ℃ 人工录入',
  `store_time` datetime NOT NULL COMMENT '留样时间',
  `expire_time` datetime DEFAULT NULL COMMENT '保留到期时间',
  `retain_hours` int DEFAULT NULL COMMENT '保留时长',
  `photo` varchar(200) DEFAULT NULL COMMENT '留样图片（本地相对路径）',
  `proof_photo` varchar(200) DEFAULT NULL COMMENT '现场凭证图片',
  `sampler_id` bigint DEFAULT NULL,
  `sampler_name` varchar(32) DEFAULT NULL,
  `reviewer_id` bigint DEFAULT NULL,
  `review_time` datetime DEFAULT NULL,
  `review_remark` varchar(300) DEFAULT NULL,
  `status` varchar(24) NOT NULL DEFAULT 'PENDING_REVIEW' COMMENT '待复核/保留中/待销毁/已销毁/已驳回',
  `inspect_result` varchar(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/PASSED/FAILED/RECHECK',
  `locked` tinyint NOT NULL DEFAULT 0 COMMENT '被事件锁定则禁止销毁',
  `disposed` tinyint NOT NULL DEFAULT 0,
  `remark` varchar(300) DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁，防并发覆盖',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sample_no` (`sample_no`),
  KEY `idx_sample_status` (`status`),
  KEY `idx_sample_store` (`store_time`),
  KEY `idx_sample_loc` (`location_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='食品留样主档';""")

A(rows("food_sample", ["id","sample_no","food_name","category_id","meal","batch_no","batch_id","task_id","weight",
                       "container_no","location_id","location_code","temperature","store_time","expire_time","retain_hours",
                       "photo","proof_photo","sampler_id","sampler_name","reviewer_id","review_time","review_remark",
                       "status","inspect_result","locked","disposed","remark","deleted","version","create_time","update_time"],
       sam))

A("""-- ---------- 留样操作记录（时间轴，不可修改） ----------
CREATE TABLE `food_sample_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `sample_id` bigint NOT NULL,
  `action` varchar(32) NOT NULL COMMENT 'CREATE/SUBMIT/REVIEW_PASS/REVIEW_REJECT/LOCK/UNLOCK/DISPOSE',
  `from_status` varchar(24) DEFAULT NULL,
  `to_status` varchar(24) DEFAULT NULL,
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(32) DEFAULT NULL,
  `reason` varchar(300) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_rec_sample` (`sample_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='留样操作记录';""")

records = []
rid = 1
for i, food, cat, meal, bi, w in SAMPLES:
    st = status_of(i)
    day = day_offset(i)
    hh = 12 if meal == "午餐" else 7
    records.append((rid, i, "CREATE", None, "PENDING_REVIEW", SAMPLERS[i % 4][0],
                    SAMPLERS[i % 4][1],
                    "按任务创建留样记录", t(day, hh, 5))); rid += 1
    if st in ("RETAINING", "PENDING_DISPOSE", "DISPOSED"):
        records.append((rid, i, "REVIEW_PASS", "PENDING_REVIEW", "RETAINING", 7, "王建国",
                        "重量、密封、标识、仓位核对通过", t(day, hh + 1, 15))); rid += 1
    if st == "PENDING_DISPOSE":
        records.append((rid, i, "EXPIRE", "RETAINING", "PENDING_DISPOSE", None, "系统",
                        "到达保留时限，转入待销毁", t(day + 2, hh, 0))); rid += 1
    if st == "DISPOSED":
        records.append((rid, i, "DISPOSE", "PENDING_DISPOSE", "DISPOSED", 1, "李秀兰",
                        "双人监督销毁完成，数据锁定", t(day + 3, 15, 20))); rid += 1
    if st == "REJECTED":
        records.append((rid, i, "REVIEW_REJECT", "PENDING_REVIEW", "REJECTED", 8, "刘敏",
                        "实际重量低于标准，容器密封不严，需重新留样", t(day, hh + 1, 10))); rid += 1
A(rows("food_sample_record", ["id","sample_id","action","from_status","to_status","operator_id","operator_name","reason","create_time"], records))

A("""-- ---------- 留样附件 ----------
CREATE TABLE `food_sample_attachment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `sample_id` bigint NOT NULL,
  `file_name` varchar(120) NOT NULL COMMENT '展示名',
  `path` varchar(200) NOT NULL COMMENT '相对路径，不存外链',
  `file_type` varchar(16) NOT NULL DEFAULT 'IMAGE' COMMENT 'IMAGE/PDF',
  `file_size` bigint DEFAULT 0,
  `uploader_name` varchar(32) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_att_sample` (`sample_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='留样附件';""")

atts = []
aid = 1
for i, *_ in SAMPLES:
    day = day_offset(i)
    hh = 12
    atts.append((aid, i, "留样容器照片", f"/uploads/seed/dish/{((i*7)%12)+1:02d}.jpg", "IMAGE", 186000 + i * 1200, SAMPLERS[i % 4][1], t(day, hh, 8))); aid += 1
    atts.append((aid, i, "现场凭证照片", f"/uploads/seed/proof/{((i*5)%6)+1:02d}.jpg", "IMAGE", 286000 + i * 1400, SAMPLERS[i % 4][1], t(day, hh, 9))); aid += 1
A(rows("food_sample_attachment", ["id","sample_id","file_name","path","file_type","file_size","uploader_name","create_time"], atts))

# ---------------- 任务 ----------------
A("""-- ---------- 留样任务 ----------
CREATE TABLE `sample_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_no` varchar(40) NOT NULL,
  `batch_id` bigint NOT NULL,
  `batch_no` varchar(40) NOT NULL,
  `food_name` varchar(64) NOT NULL,
  `category_id` bigint NOT NULL,
  `meal` varchar(16) NOT NULL,
  `rule_id` bigint DEFAULT NULL,
  `min_weight` decimal(10,1) NOT NULL,
  `storage_cond` varchar(64) DEFAULT NULL,
  `deadline` datetime NOT NULL COMMENT '留样截止时间',
  `status` varchar(24) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待执行/已完成各状态/OVERDUE逾期',
  `sampler_id` bigint DEFAULT NULL,
  `sampler_name` varchar(32) DEFAULT NULL,
  `sample_id` bigint DEFAULT NULL COMMENT '完成后关联的留样',
  `remark` varchar(200) DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_no` (`task_no`),
  KEY `idx_task_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='留样任务（按批次与规则自动生成）';""")

tasks = []
tid = 1
for i, food, cat, meal, bi, w in SAMPLES:
    st = status_of(i)
    day = day_offset(i)
    hh = 12 if meal == "午餐" else 7
    tasks.append((tid, f"RW{batches[bi-1][0]}-{i:02d}", bi, batches[bi-1][0], food, cat, meal,
                  1 if cat == 1 else (2 if cat == 2 else (3 if cat == 3 else (4 if cat == 4 else 5))),
                  125.0 if cat in (1, 2) else (100.0 if cat == 3 else (250.0 if cat == 4 else 150.0)),
                  "0–4 ℃ 冷藏", t(day, hh + 1, 0), st, SAMPLERS[i % 4][0],
                  SAMPLERS[i % 4][1], i, None, 0,
                  t(day, hh - 2, 30), t(day, hh, 10))); tid += 1

# 今日待执行任务
pending_today = [
    ("蒜蓉西兰花",2,"午餐",2,1,"李秀兰"),("清炒时蔬",2,"午餐",3,2,"周慧敏"),("紫菜蛋花汤",4,"午餐",4,4,"何芳"),
]
for j, (food, cat, meal, bi, sp_id, sp_name) in enumerate(pending_today, 1):
    tasks.append((tid, f"RW20261031-{90+j:02d}", bi, "PC-20261031-L01" if bi in (2,3,5,6) else "PC-20261031-B01",
                  food, cat, meal, 1, 125.0 if cat in (1, 2) else (100.0 if cat == 3 else (250.0 if cat == 4 else 150.0)),
                  "0–4 ℃ 冷藏", t(0, 13, 0), "PENDING", sp_id,
                  sp_name, None, None, 0,
                  t(0, 6, 30), t(0, 6, 30))); tid += 1
# 2 条逾期任务
for j, (food, cat) in enumerate([("水煮鱼片",1),("蒜泥白肉",2)], 1):
    tasks.append((tid, f"RW20261030-{80+j:02d}", 5, "PC-20261030-L01", food, cat, "午餐", 1,
                  125.0, "0–4 ℃ 冷藏", t(-1, 13, 0), "OVERDUE", 1, "李秀兰", None,
                  "超时未完成留样，已计入完成率考核", 0, t(-1, 6, 30), t(-1, 13, 1))); tid += 1

A(rows("sample_task", ["id","task_no","batch_id","batch_no","food_name","category_id","meal","rule_id","min_weight",
                        "storage_cond","deadline","status","sampler_id","sampler_name","sample_id","remark","deleted",
                        "create_time","update_time"], tasks))

# ---------------- 检测 ----------------
A("""-- ---------- 检测记录 ----------
CREATE TABLE `inspection_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `inspect_no` varchar(40) NOT NULL,
  `sample_id` bigint NOT NULL,
  `sample_no` varchar(50) NOT NULL,
  `food_name` varchar(64) NOT NULL,
  `inspect_type` varchar(16) NOT NULL COMMENT '感官/微生物/添加剂/综合',
  `inspect_method` varchar(64) DEFAULT NULL COMMENT '抽检方式 随机/指定',
  `item_count` int NOT NULL DEFAULT 0,
  `result` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/QUALIFIED/UNQUALIFIED/RECHECK',
  `status` varchar(24) NOT NULL DEFAULT 'PENDING' COMMENT '待检测/检测中/待复核/已合格/已不合格',
  `inspector_id` bigint DEFAULT NULL,
  `inspector_name` varchar(32) DEFAULT NULL,
  `inspect_time` datetime DEFAULT NULL,
  `reviewer_id` bigint DEFAULT NULL,
  `review_time` datetime DEFAULT NULL,
  `report_file` varchar(200) DEFAULT NULL COMMENT '报告附件相对路径',
  `remark` varchar(300) DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inspect_no` (`inspect_no`),
  KEY `idx_insp_sample` (`sample_id`),
  KEY `idx_insp_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检测记录';""")

A("""-- ---------- 检测项目 ----------
CREATE TABLE `inspection_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `record_id` bigint NOT NULL,
  `item_name` varchar(64) NOT NULL,
  `standard_value` varchar(64) DEFAULT NULL,
  `actual_value` varchar(64) DEFAULT NULL,
  `judge` varchar(16) NOT NULL DEFAULT 'QUALIFIED' COMMENT 'QUALIFIED/UNQUALIFIED',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_item_record` (`record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检测项目';""")

INS_TYPES = [
    ("微生物", "随机抽检", [("菌落总数","≤ 10⁵ CFU/g","2.3×10⁴","QUALIFIED"),("大肠菌群","≤ 100 MPN/g","26","QUALIFIED"),("沙门氏菌","不得检出","未检出","QUALIFIED")]),
    ("感官", "指定抽检", [("色泽","正常","正常","QUALIFIED"),("气味","无异味","无异味","QUALIFIED"),("组织形态","完整","完整","QUALIFIED"),("杂质","无","无","QUALIFIED")]),
    ("添加剂", "随机抽检", [("亚硝酸盐","≤ 30 mg/kg","4.1","QUALIFIED"),("苯甲酸","≤ 1.0 g/kg","未检出","QUALIFIED")]),
    ("综合", "指定抽检", [("菌落总数","≤ 10⁵ CFU/g","3.1×10⁴","QUALIFIED"),("大肠菌群","≤ 100 MPN/g","38","QUALIFIED"),("亚硝酸盐","≤ 30 mg/kg","3.2","QUALIFIED"),("感官","正常","正常","QUALIFIED"),("净含量","≥ 标注值","达标","QUALIFIED"),("中心温度","≥ 60 ℃","68","QUALIFIED")]),
]
ins = []
items = []
iid = 1
for k in range(36):
    sid = (k * 7) % 30 + 1
    day = day_offset(sid)
    tkey = INS_TYPES[k % 4]
    if k in (2, 9, 21, 33):
        result, st = "UNQUALIFIED", "UNQUALIFIED"
    elif k in (4, 14, 26):
        result, st = "RECHECK", "PENDING_REVIEW"
    elif k < 32:
        result, st = "QUALIFIED", "QUALIFIED"
    else:
        result, st = "PENDING", "TESTING"
    inspector = 5 if k % 2 == 0 else 6
    insp_name = "陈志远" if inspector == 5 else "吴倩"
    insp_t = t(day, 14, (k * 11) % 55)
    row_items = tkey[2]
    if result == "UNQUALIFIED":
        row_items = [(n, s, ("1.2×10⁶" if "菌落" in n else ("超限" if "亚硝" in n else "检出")), "UNQUALIFIED") for (n, s, a, j) in row_items]
    ins.append((k + 1, f"JC{t(day)[:10].replace('-','')}-{k + 1:04d}", sid,
                f"LY{t(day)[:10].replace('-','')}-{sid:04d}", DISHES[sid - 1][0],
                tkey[0], tkey[1], len(row_items), result, st, inspector, insp_name,
                insp_t if result != "PENDING" else None,
                7 if st in ("QUALIFIED", "UNQUALIFIED") else None,
                t(day, 16, 30) if st in ("QUALIFIED", "UNQUALIFIED") else None,
                None, "按抽检计划执行" if result != "UNQUALIFIED" else "检出超标，已通知主管封存",
                0, t(day, 10, 0), insp_t))
    for (n, s, a, j) in row_items:
        items.append((iid, k + 1, n, s, a, j, insp_t)); iid += 1

A(rows("inspection_record", ["id","inspect_no","sample_id","sample_no","food_name","inspect_type","inspect_method",
                              "item_count","result","status","inspector_id","inspector_name","inspect_time","reviewer_id",
                              "review_time","report_file","remark","deleted","create_time","update_time"], ins))
A(rows("inspection_item", ["id","record_id","item_name","standard_value","actual_value","judge","create_time"], items))

# ---------------- 事件 ----------------
A("""-- ---------- 不合格事件 ----------
CREATE TABLE `food_incident` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `incident_no` varchar(40) NOT NULL,
  `sample_id` bigint NOT NULL,
  `sample_no` varchar(50) NOT NULL,
  `food_name` varchar(64) NOT NULL,
  `incident_type` varchar(32) NOT NULL COMMENT '微生物超标/添加剂超限/感官异常',
  `severity` varchar(16) NOT NULL COMMENT 'GENERAL一般/SERIOUS严重/MAJOR重大',
  `status` varchar(24) NOT NULL DEFAULT 'HANDLING' COMMENT 'HANDLING处置中 FOLLOWING待跟进结论 CLOSED已关闭',
  `occur_time` datetime NOT NULL,
  `discoverer` varchar(32) DEFAULT NULL,
  `seal_location` varchar(64) DEFAULT NULL COMMENT '封存仓位',
  `stop_supply` varchar(200) DEFAULT NULL COMMENT '停供范围',
  `trace_batch` varchar(200) DEFAULT NULL COMMENT '同批次排查结论',
  `conclusion` varchar(400) DEFAULT NULL COMMENT '跟进结论',
  `close_time` datetime DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_incident_no` (`incident_no`),
  KEY `idx_inc_sample` (`sample_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不合格事件';""")

inc = [
    (1,"INC20261031-003",6,"凉拌木耳","微生物超标","SERIOUS","HANDLING",t(0,13,45),"陈志远","B-1-03","凉菜间同批次 12 份全部撤柜停供","追溯批次 PC-20261031-L02，涉及 3 个档口，同批次抽样 2 份待检",None,None),
    (2,"INC20261029-002",18,"红烧排骨","添加剂超限","MAJOR","FOLLOWING",t(-2,16,20),"吴倩","A-3-05","二食堂热菜档停供 1 天","追溯调料供应商味美调味品商行，抽检同批调料 2 份","", None),
    (3,"INC20261018-001",34,"红烧肉","感官异常","GENERAL","CLOSED",t(-13,9,10),"陈志远","A-1-05","单个菜品撤柜","同批次其余菜品感官正常，原因为复热时间过长","责令整改并加强复热工序管控，已完成培训",t(-12,10,0)),
    (4,"INC20261007-001",30,"八宝粥","微生物超标","SERIOUS","CLOSED",t(-24,14,5),"吴倩","C-2-03","面点档停供半日","同批次 3 份留样仅 1 份超标，疑为容器消毒不彻底","更换消毒流程，复检合格后恢复供餐",t(-22,15,30)),
    (5,"INC20260915-001",27,"酸辣土豆丝","感官异常","GENERAL","CLOSED",t(-46,11,20),"陈志远","B-2-02","无","原料土豆发芽，已退货","供应商退货并列入观察名单",t(-45,9,0)),
    (6,"INC20260812-001",33,"香菇青菜","微生物超标","SERIOUS","CLOSED",t(-80,13,40),"吴倩","A-1-07","素菜档停供 1 天","冷藏柜温度偏高 6.2 ℃，已维修","维修冷藏柜并加装温度记录",t(-78,16,0)),
    (7,"INC20260718-001",31,"红烧带鱼","添加剂超限","MAJOR","CLOSED",t(-105,15,10),"陈志远","A-2-06","水产类全部停供","供应商东海水产批发部资质过期","更换供应商并重新审核资质",t(-100,10,0)),
    (8,"INC20260522-001",36,"花卷","感官异常","GENERAL","CLOSED",t(-162,8,30),"吴倩","B-1-01","面点档停供半日","发酵时间不足","调整工艺参数，复检合格",t(-161,9,0)),
]
A(rows("food_incident", ["id","incident_no","sample_id","sample_no","food_name","incident_type","severity","status",
                          "occur_time","discoverer","seal_location","stop_supply","trace_batch","conclusion","close_time",
                          "deleted","create_time","update_time"],
       [(a, b, c, f"LY{t(day_offset(c))[:10].replace('-','')}-{c:04d}", d, e, f, g, h, i, j, k, l, m, n, 0, h, n or h)
        for (a, b, c, d, e, f, g, h, i, j, k, l, m, n) in inc]))

# ---------------- 销毁 ----------------
A("""-- ---------- 销毁记录 ----------
CREATE TABLE `disposal_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `disposal_no` varchar(40) NOT NULL,
  `sample_id` bigint NOT NULL,
  `sample_no` varchar(50) NOT NULL,
  `food_name` varchar(64) NOT NULL,
  `method` varchar(64) NOT NULL COMMENT '销毁方式',
  `dispose_time` datetime NOT NULL,
  `executor` varchar(32) NOT NULL COMMENT '执行人',
  `supervisor` varchar(32) NOT NULL COMMENT '监督人，须与执行人不同',
  `weight` decimal(10,1) NOT NULL COMMENT '销毁重量 g',
  `photo` varchar(200) DEFAULT NULL COMMENT '销毁照片本地路径',
  `status` varchar(24) NOT NULL DEFAULT 'PENDING_APPROVE' COMMENT 'PENDING_APPROVE/APPROVED',
  `approve_by` varchar(32) DEFAULT NULL,
  `approve_time` datetime DEFAULT NULL,
  `remark` varchar(300) DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_disposal_no` (`disposal_no`),
  KEY `idx_dis_sample` (`sample_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销毁记录（双人监督）';""")

disp = []
for j, sid in enumerate([25, 26, 27, 28, 29, 30, 19, 20, 21, 22], 1):
    st = status_of(sid)
    day = day_offset(sid) + 3
    approved = st == "DISPOSED"
    disp.append((j, f"XH20261031-{j:03d}", sid,
                 f"LY{t(day_offset(sid))[:10].replace('-','')}-{sid:04d}", DISHES[sid - 1][0],
                 ["餐厨垃圾统一处理", "高温灭菌后处理", "专业机构回收"][j % 3],
                 t(day, 15, 20 + j), ["李秀兰", "周慧敏", "何芳"][j % 3], ["王建国", "刘敏"][j % 2],
                 float(DISHES[sid - 1][4]), f"/uploads/seed/disposal/{((j*3)%4)+1:02d}.jpg",
                 "APPROVED" if approved else "PENDING_APPROVE",
                 "王建国" if approved else None, t(day, 16, 40) if approved else None,
                 "双人监督下完成销毁" if approved else "已提交，待主管审批", 0, t(day, 15, 10), t(day, 15, 20)))
A(rows("disposal_record", ["id","disposal_no","sample_id","sample_no","food_name","method","dispose_time","executor",
                            "supervisor","weight","photo","status","approve_by","approve_time","remark","deleted",
                            "create_time","update_time"], disp))

# ---------------- 创新点：规则与评分 ----------------
A("""-- ---------- 校验规则（创新点，权重合计 100） ----------
CREATE TABLE `eval_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `version` varchar(16) NOT NULL,
  `name` varchar(64) NOT NULL COMMENT '规则名称',
  `weights` json NOT NULL COMMENT '各因素权重，合计 100',
  `thresholds` json NOT NULL COMMENT '等级阈值',
  `effective_time` datetime NOT NULL,
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1生效 0历史',
  `remark` varchar(200) DEFAULT NULL,
  `create_by` varchar(32) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rule_version` (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='留样覆盖率与到期处置校验规则';""")

W3 = '{"menuBatch":15,"shouldCount":20,"actualWeight":20,"storeTime":15,"retainDuration":15,"evidence":15}'
W2 = '{"menuBatch":18,"shouldCount":22,"actualWeight":22,"storeTime":16,"retainDuration":22}'
W1 = '{"shouldCount":40,"actualWeight":40,"storeTime":20}'
TH = '{"excellent":85,"good":70,"medium":55}'
A(rows("eval_rule", ["id","version","name","weights","thresholds","effective_time","status","remark","create_by","create_time"], [
    (1, "v1", "基础重量校验", W1, TH, "2026-03-01 00:00:00", 0, "仅按重量与仓位做基础校验", "王建国", t(-240)),
    (2, "v2", "到期处置校验", W2, TH, "2026-06-15 00:00:00", 0, "首次引入到期处置校验", "王建国", t(-138)),
    (3, "v3", "留样覆盖率与到期处置校验", W3, TH, "2026-09-01 00:00:00", 1, "提高处置凭证权重，新增入柜时间因素", "王建国", t(-60)),
]))

A("""-- ---------- 校验计算结果（输入快照 + 明细，可追溯） ----------
CREATE TABLE `eval_score` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `eval_no` varchar(40) NOT NULL,
  `biz_date` date NOT NULL COMMENT '业务日期',
  `rule_id` bigint NOT NULL,
  `rule_version` varchar(16) NOT NULL COMMENT '计算时使用的规则版本',
  `input_json` json NOT NULL COMMENT '输入快照，保证同输入同结果',
  `detail_json` json NOT NULL COMMENT '各因素得分明细',
  `total_score` decimal(5,1) NOT NULL,
  `grade` varchar(8) NOT NULL COMMENT '优/良/中/差',
  `recommend` varchar(200) NOT NULL COMMENT '推荐结果',
  `unmet_json` json DEFAULT NULL COMMENT '未满足条件',
  `improve_json` json DEFAULT NULL COMMENT '可改进项',
  `calc_time` datetime NOT NULL,
  `adopted` tinyint NOT NULL DEFAULT 0 COMMENT '是否采纳推荐',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_eval_no` (`eval_no`),
  KEY `idx_eval_date` (`biz_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='校验计算结果';""")

A("""-- ---------- 人工修正记录（原始结果不覆盖） ----------
CREATE TABLE `eval_adjust` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `score_id` bigint NOT NULL,
  `before_grade` varchar(8) NOT NULL,
  `after_grade` varchar(8) NOT NULL,
  `reason` varchar(300) NOT NULL COMMENT '调整原因，不少于 5 个字',
  `operator` varchar(32) NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_adj_score` (`score_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='校验结果人工修正';""")

scores = [
    (1,"EV20261031-0027","2026-10-31",3,"v3",'{"menuBatch":"MU-20261031-L","shouldCount":13,"actualCount":10,"avgWeight":133.6,"lateStore":2,"retainOk":10,"evidenceRate":0.25}',84.0,"良","可进入销毁流程，需补齐销毁照片与监督人签字",0),
    (2,"EV20261030-0026","2026-10-30",3,"v3",'{"menuBatch":"MU-20261030-L","shouldCount":8,"actualCount":8,"avgWeight":130.2,"lateStore":1,"retainOk":7,"evidenceRate":0.2}',76.5,"良","建议暂缓处置，先补齐凭证",1),
    (3,"EV20261029-0025","2026-10-29",3,"v3",'{"menuBatch":"MU-20261029-L","shouldCount":6,"actualCount":6,"avgWeight":134.1,"lateStore":0,"retainOk":6,"evidenceRate":0.35}',88.0,"优","可按期处置",1),
    (4,"EV20261028-0024","2026-10-28",3,"v3",'{"menuBatch":"MU-20261028-L","shouldCount":6,"actualCount":5,"avgWeight":126.2,"lateStore":1,"retainOk":5,"evidenceRate":0.18}',68.0,"中","建议加强留样及时性后再处置",0),
    (5,"EV20261027-0023","2026-10-27",3,"v3",'{"menuBatch":"MU-20261027-B","shouldCount":4,"actualCount":4,"avgWeight":110.5,"lateStore":0,"retainOk":4,"evidenceRate":0.6}',82.0,"良","面点类重量偏低，需关注",1),
    (6,"EV20261026-0022","2026-10-26",3,"v3",'{"menuBatch":"MU-20261026-L","shouldCount":4,"actualCount":4,"avgWeight":135.0,"lateStore":0,"retainOk":4,"evidenceRate":0.9}',94.0,"优","推荐按期处置，凭证齐全",1),
    (7,"EV20261024-0021","2026-10-24",3,"v3",'{"menuBatch":"MU-20261024-B","shouldCount":4,"actualCount":3,"avgWeight":118.4,"lateStore":1,"retainOk":3,"evidenceRate":0.1}',52.0,"差","不建议处置，先整改留样覆盖率",0),
    (8,"EV20261018-0020","2026-10-18",3,"v3",'{"menuBatch":"MU-20261018-L","shouldCount":4,"actualCount":4,"avgWeight":130.2,"lateStore":1,"retainOk":3,"evidenceRate":0.4}',79.0,"良","补齐凭证后可处置",1),
]
detail = '{"menuBatch":{"weight":15,"score":15.0},"shouldCount":{"weight":20,"score":20.0},"actualWeight":{"weight":20,"score":18.0},"storeTime":{"weight":15,"score":12.0},"retainDuration":{"weight":15,"score":15.0},"evidence":{"weight":15,"score":4.0}}'
A(rows("eval_score", ["id","eval_no","biz_date","rule_id","rule_version","input_json","detail_json","total_score","grade",
                       "recommend","unmet_json","improve_json","calc_time","adopted","create_time"],
       [(a,b,c,d,e,f,detail,g,h,i,
         '["销毁照片缺失，14 份待销毁中仅 3 份有现场照片","监督人签字缺失，未完成双人确认","2 份留样入柜时间晚于出餐后 2 小时"]' if a == 1 else '["部分留样缺少销毁照片"]',
         '["补齐销毁现场照片（每份 1 张，含容器与秤重）","邀请监督人到场并在处置单签字","凉菜间出餐后 1 小时内完成留样入柜"]' if a == 1 else '["补齐凭证后重新计算"]',
         t(0 if a == 1 else -(a % 3), 15, 42 - a), j, t(0 if a == 1 else -(a % 3), 15, 42 - a))
        for (a,b,c,d,e,f,g,h,i,j) in scores]))

A(rows("eval_adjust", ["id","score_id","before_grade","after_grade","reason","operator","create_time"], [
    (1, 1, "中", "良", "已补 3 张销毁照片，现场条件改善", "王建国", t(0, 15, 50)),
    (2, 4, "中", "中", "复核后维持原等级，留样及时性仍未达标", "刘敏", t(-3, 10, 20)),
    (3, 7, "差", "中", "当日临时增加供餐批次，已补录说明", "王建国", t(-7, 9, 5)),
]))

# ---------------- 消息 / 文件 / 日志 / 字典 ----------------
A("""-- ---------- 站内消息 ----------
CREATE TABLE `sys_message` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `title` varchar(120) NOT NULL,
  `content` varchar(500) NOT NULL,
  `biz_type` varchar(24) DEFAULT NULL,
  `biz_id` bigint DEFAULT NULL,
  `is_read` tinyint NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_msg_user` (`user_id`,`is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内消息';""")
A(rows("sys_message", ["id","user_id","title","content","biz_type","biz_id","is_read","create_time"], [
    (1,1,"留样复核驳回","凉拌木耳（LY20261031-0006）实际重量 108 g 低于标准 125 g，请重新留样","SAMPLE",6,0,t(0,11,52)),
    (2,1,"即将到期提醒","3 份留样将于今日到期，请配合主管完成处置","SAMPLE",19,0,t(0,8,0)),
    (3,1,"任务生成通知","午餐批次共 12 项留样任务已生成，截止 13:00","TASK",None,1,t(0,6,30)),
    (4,1,"待复核提醒","您提交的 LY20261031-0002 已进入复核队列","SAMPLE",2,1,t(0,12,10)),
    (5,5,"检测任务派发","今日随机抽检 5 项已派发，请按计划完成","INSPECT",1,0,t(0,9,0)),
    (6,5,"结果待复核","清蒸鲈鱼检测结果已提交，等待主管复核","INSPECT",1,0,t(0,14,25)),
    (7,7,"不合格事件待关闭","INC20261031-003 凉拌木耳微生物超标，请填写跟进结论","INCIDENT",1,0,t(0,13,50)),
    (8,7,"销毁申请待审批","紫菜蛋花汤（LY20261028-0009）已提交销毁申请","DISPOSAL",2,0,t(0,15,30)),
    (9,7,"销毁超期预警","3 份留样已超过保留时限仍未销毁","SAMPLE",19,0,t(0,7,30)),
    (10,9,"登录锁定提醒","用户 liuyang03 连续 5 次登录失败，账号已锁定 10 分钟","USER",3,1,t(-1,9,30)),
]))

A("""-- ---------- 文件 ----------
CREATE TABLE `sys_file` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `biz_type` varchar(32) DEFAULT NULL,
  `biz_id` bigint DEFAULT NULL,
  `file_name` varchar(160) NOT NULL COMMENT '存储文件名',
  `original_name` varchar(160) NOT NULL,
  `path` varchar(200) NOT NULL COMMENT '相对路径',
  `file_size` bigint NOT NULL DEFAULT 0,
  `content_type` varchar(80) DEFAULT NULL,
  `uploader_id` bigint DEFAULT NULL,
  `uploader_name` varchar(32) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件与附件元数据（不存外链）';""")
A(rows("sys_file", ["id","biz_type","biz_id","file_name","original_name","path","file_size","content_type","uploader_id","uploader_name","create_time"], [
    (1,"SAMPLE",1,"sample_1_1801.jpg","留样容器.jpg","/uploads/seed/dish/08.jpg",186200,"image/jpeg",1,"李秀兰",t(0,12,8)),
    (2,"SAMPLE",1,"proof_1_1802.jpg","现场凭证.jpg","/uploads/seed/proof/06.jpg",289100,"image/jpeg",1,"李秀兰",t(0,12,9)),
    (3,"DISPOSAL",1,"disposal_1.jpg","销毁现场.jpg","/uploads/seed/disposal/04.jpg",242000,"image/jpeg",1,"李秀兰",t(0,15,20)),
    (4,"INSPECT",1,"report_1_1836.jpg","检测报告照片.jpg","/uploads/seed/proof/01.jpg",174000,"image/jpeg",5,"陈志远",t(0,14,30)),
    (5,"AVATAR",1,"avatar_1.jpg","头像.jpg","/uploads/seed/avatar/02.jpg",96000,"image/jpeg",1,"李秀兰",t(-20,10,0)),
]))

A("""-- ---------- 操作日志 ----------
CREATE TABLE `sys_operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL,
  `user_name` varchar(32) DEFAULT NULL,
  `action` varchar(64) NOT NULL,
  `target` varchar(64) DEFAULT NULL,
  `detail` varchar(500) DEFAULT NULL,
  `result` varchar(16) NOT NULL DEFAULT 'SUCCESS',
  `ip` varchar(40) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_log_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志';""")
logs = [
    (7,"王建国","人工修正校验等级","EV20261031-0027","中 → 良，原因：已补 3 张销毁照片","SUCCESS",t(0,15,50)),
    (None,"系统","执行到期处置校验","当日午餐批次","规则 v3，得分 84.0","SUCCESS",t(0,15,42)),
    (5,"陈志远","提交检测结果","JC20261031-0004","综合结论：合格","SUCCESS",t(0,14,20)),
    (5,"陈志远","生成不合格事件","INC20261031-003","凉拌木耳 微生物超标","SUCCESS",t(0,13,45)),
    (1,"李秀兰","提交留样复核","LY20261031-0007","重量 132.5 g，入柜 A-3-02","SUCCESS",t(0,12,18)),
    (2,"周慧敏","复核驳回","LY20261031-0006","重量 108 g 低于标准","FAIL",t(0,11,52)),
    (7,"王建国","审批销毁","XH20261031-001","双人监督，监督人 刘敏","SUCCESS",t(0,10,5)),
    (9,"系统管理员","修改用户状态","liuyang03","锁定账号","SUCCESS",t(-1,9,30)),
    (5,"陈志远","登录","-","登录成功","SUCCESS",t(-1,9,5)),
    (3,"孙立","登录","-","密码连续错误 5 次，账号锁定","FAIL",t(-1,9,28)),
]
A(rows("sys_operation_log", ["id","user_id","user_name","action","target","detail","result","ip","create_time"],
       [(i, a, b, c, d, e, f, "10.12.33.%d" % (100 + i), g) for i, (a, b, c, d, e, f, g) in enumerate(logs, 1)]))

A("""-- ---------- 字典 ----------
CREATE TABLE `sys_dict_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dict_type` varchar(40) NOT NULL,
  `label` varchar(64) NOT NULL,
  `value` varchar(40) NOT NULL,
  `sort` int NOT NULL DEFAULT 0,
  `status` tinyint NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict` (`dict_type`,`value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典项';""")

dict = []
d = 1
for t_, items in [
    ("meal", [("早餐","BREAKFAST"),("午餐","LUNCH"),("晚餐","DINNER")]),
    ("sample_status", [("待复核","PENDING_REVIEW"),("保留中","RETAINING"),("待销毁","PENDING_DISPOSE"),("已销毁","DISPOSED"),("已驳回","REJECTED")]),
    ("inspect_type", [("感官","SENSORY"),("微生物","MICROBE"),("添加剂","ADDITIVE"),("综合","COMPREHENSIVE")]),
    ("inspect_result", [("合格","QUALIFIED"),("不合格","UNQUALIFIED"),("待复检","RECHECK")]),
    ("disposal_method", [("餐厨垃圾统一处理","KITCHEN"),("高温灭菌后处理","STERILIZE"),("专业机构回收","RECYCLE")]),
    ("incident_severity", [("一般","GENERAL"),("严重","SERIOUS"),("重大","MAJOR")]),
    ("inspect_method", [("随机抽检","RANDOM"),("指定抽检","ASSIGNED")]),
]:
    for s_, (label, val) in enumerate(items, 1):
        dict.append((d, t_, label, val, s_, 1)); d += 1
A(rows("sys_dict_item", ["id","dict_type","label","value","sort","status"], dict))

# ---------------- 视图辅助索引 ----------------
A("""-- ---------- 常用索引补充 ----------
CREATE INDEX `idx_task_batch` ON `sample_task` (`batch_id`);
CREATE INDEX `idx_sample_batch` ON `food_sample` (`batch_id`);
CREATE INDEX `idx_batch_produce` ON `food_batch` (`produce_time`);
CREATE INDEX `idx_insp_time` ON `inspection_record` (`inspect_time`);

-- ===================== 统计核对 =====================
-- 留样 36 条：待复核 4 / 保留中 14 / 待销毁 6 / 已销毁 6 / 已驳回 6
-- 检测 36 条：合格 26 / 不合格 4 / 待复检 3 / 待检测 3
-- 事件 8 条：处置中 1 / 待跟进 1 / 已关闭 6
-- 销毁 10 条：已审批 6 / 待审批 4
-- 任务 50 条：含 12 条今日待执行、2 条逾期
""")

OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_text("\n".join(sql), encoding="utf-8")
print("written", OUT, OUT.stat().st_size, "bytes")
print("samples", len(sam), "tasks", len(tasks), "insp", len(ins), "items", len(items))
