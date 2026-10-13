-- 政策与资讯种子数据（由 scripts/generate-news-seed.mjs 生成，请勿手工编辑）
--
-- 数据来源：data/policy-sources.json，每条都带可打开的原文链接。
-- city_code 为 NULL 表示不限城市（国家层面或全国性平台政策），在所有城市都可见；
-- 非空时只在该城市可见，并在该城市排到最前面。
-- source_type 区分官方（政府/群团/官方媒体）与平台企业自行发布。

USE friendly_station;

DELETE FROM news;

INSERT INTO news (title, category, content, view_count, status, source_type, source_level, publisher, platform, city_code, published_at, source_url) VALUES
  ('中共中央办公厅 国务院办公厅关于加强新就业群体服务管理的意见', '权益保障', '中办国办首个新就业群体服务管理专项意见，部署党建、权益、算法治理与友好场景建设。
发布方：中共中央办公厅、国务院办公厅
发布日期：2025-10-29
适用范围：全国
原文链接：https://www.court.gov.cn/xinshidai/xiangqing/498441.html', 0, 'PUBLISHED', 'OFFICIAL', 'AUTHORITATIVE', '中共中央办公厅、国务院办公厅', NULL, NULL, '2025-10-29', 'https://www.court.gov.cn/xinshidai/xiangqing/498441.html'),
  ('关于维护新就业形态劳动者劳动保障权益的指导意见（人社部发〔2021〕56号）', '权益保障', '新就业形态权益保障的纲领性文件，确立劳动关系三分法与平台算法、申诉等责任。
发布方：人力资源社会保障部等八部门
发布日期：2021-07-16
适用范围：全国
原文链接：https://www.gov.cn/zhengce/zhengceku/2021-07/23/content_5626761.htm', 0, 'PUBLISHED', 'OFFICIAL', 'SECONDARY', '人力资源社会保障部等八部门', NULL, NULL, '2021-07-16', 'https://www.gov.cn/zhengce/zhengceku/2021-07/23/content_5626761.htm'),
  ('关于扩大新就业形态人员职业伤害保障试点的通知（人社部发〔2025〕24号）', '职业伤害', '三年分步扩围职业伤害保障试点，新增十省份与滴滴、顺丰同城、满帮等平台。
发布方：人力资源社会保障部等九部门
发布日期：2025-04-22
适用范围：全国
原文链接：https://www.gov.cn/gongbao/2025/issue_12186/202507/content_7034087.html', 0, 'PUBLISHED', 'OFFICIAL', 'SECONDARY', '人力资源社会保障部等九部门', NULL, NULL, '2025-04-22', 'https://www.gov.cn/gongbao/2025/issue_12186/202507/content_7034087.html'),
  ('《新就业形态劳动者权益保障办法》公开征求意见', '权益保障', '新就业形态权益保障拟由指导意见升格为部门规章，公开向社会征求意见。
发布方：智慧普法平台（司法部，转载人力资源社会保障部征求意见稿）
发布日期：2026-10-09
适用范围：全国
原文链接：http://legalinfo.moj.gov.cn/zhfxfzzx/fzzxyw/202610/t20261009_540436.html', 0, 'PUBLISHED', 'OFFICIAL', 'AUTHORITATIVE', '智慧普法平台（司法部，转载人力资源社会保障部征求意见稿）', NULL, NULL, '2026-10-09', 'http://legalinfo.moj.gov.cn/zhfxfzzx/fzzxyw/202610/t20261009_540436.html'),
  ('我国将抓紧出台新就业形态劳动者权益保障办法', '权益保障', '人社部门表态将抓紧出台新就业形态劳动者权益保障办法，完善制度供给。
发布方：中国就业网（人力资源社会保障部主管）
发布日期：2026-01-09
适用范围：全国
原文链接：https://chinajob.mohrss.gov.cn/h5/c/2026-01-09/478694.shtml', 0, 'PUBLISHED', 'OFFICIAL', 'AUTHORITATIVE', '中国就业网（人力资源社会保障部主管）', NULL, NULL, '2026-01-09', 'https://chinajob.mohrss.gov.cn/h5/c/2026-01-09/478694.shtml'),
  ('为灵活就业、新就业形态劳动者撑起法治蓝天', '权益保障', '全国人大网站阐述以法治手段保障灵活就业与新就业形态劳动者权益的方向。
发布方：中国人大网
发布日期：2026-02-27
适用范围：全国
原文链接：http://www.npc.gov.cn/npc/c2/c30834/202602/t20260227_451738.html', 0, 'PUBLISHED', 'OFFICIAL', 'AUTHORITATIVE', '中国人大网', NULL, NULL, '2026-02-27', 'http://www.npc.gov.cn/npc/c2/c30834/202602/t20260227_451738.html'),
  ('七部门联合印发关于做好快递员群体合法权益保障工作的意见', '权益保障', '七部门明确快递员派费、社保、休息与投诉救济等权益保障要求的专项意见。
发布方：国家邮政局等七部门
发布日期：2021-07-01
适用范围：全国
原文链接：https://www.spb.gov.cn/gjyzj/c100015/c100016/202107/8f863298776f4797a8e24bedcd7f47a7.shtml', 0, 'PUBLISHED', 'OFFICIAL', 'AUTHORITATIVE', '国家邮政局等七部门', NULL, NULL, '2021-07-01', 'https://www.spb.gov.cn/gjyzj/c100015/c100016/202107/8f863298776f4797a8e24bedcd7f47a7.shtml'),
  ('制定派费指引、创新参保方式 让快递员权益更有保障', '劳动报酬', '国家邮政局解读快递员派费指引与优先参保等配套举措，稳定末端收入。
发布方：国家邮政局
发布日期：2021-07-01
适用范围：全国
原文链接：https://www.spb.gov.cn/gjyzj/c200058/202107/4d52492973cb4ed88c5e8f9dbace01d8.shtml', 0, 'PUBLISHED', 'OFFICIAL', 'AUTHORITATIVE', '国家邮政局', NULL, NULL, '2021-07-01', 'https://www.spb.gov.cn/gjyzj/c200058/202107/4d52492973cb4ed88c5e8f9dbace01d8.shtml'),
  ('新就业群体服务管理工作部署推进会召开', '驿站服务', '中央社会工作部部署推进新就业群体服务管理，明确阵地建设与诉求响应任务。
发布方：中共中央社会工作部
发布日期：2026-04-16
适用范围：全国
原文链接：http://www.zyshgzb.gov.cn/n1/2026/0416/c462103-40702301.html', 0, 'PUBLISHED', 'OFFICIAL', 'AUTHORITATIVE', '中共中央社会工作部', NULL, NULL, '2026-04-16', 'http://www.zyshgzb.gov.cn/n1/2026/0416/c462103-40702301.html'),
  ('给新就业群体更多暖心服务', '驿站服务', '中央社会工作部阐释以暖心服务凝聚新就业群体的工作思路与阵地要求。
发布方：中共中央社会工作部
发布日期：2025-11-27
适用范围：全国
原文链接：https://www.zyshgzb.gov.cn/n1/2025/1127/c461360-40612759.html', 0, 'PUBLISHED', 'OFFICIAL', 'AUTHORITATIVE', '中共中央社会工作部', NULL, NULL, '2025-11-27', 'https://www.zyshgzb.gov.cn/n1/2025/1127/c461360-40612759.html'),
  ('不止歇脚，更有安心', '驿站服务', '介绍新就业群体服务阵地从歇脚功能向权益咨询、诉求受理等综合服务升级。
发布方：中共中央社会工作部
发布日期：2026-08-21
适用范围：全国
原文链接：https://www.zyshgzb.gov.cn/n1/2026/0821/c462551-40783844.html', 0, 'PUBLISHED', 'OFFICIAL', 'AUTHORITATIVE', '中共中央社会工作部', NULL, NULL, '2026-08-21', 'https://www.zyshgzb.gov.cn/n1/2026/0821/c462551-40783844.html'),
  ('以心暖“新” 连“新”共治（凝“新”聚力）', '权益保障', '报道地方以关爱服务引导新就业群体参与基层治理与共治的做法。
发布方：中共中央社会工作部
发布日期：2026-06-25
适用范围：全国
原文链接：https://www.zyshgzb.gov.cn/n1/2026/0625/c462300-40747278.html', 0, 'PUBLISHED', 'OFFICIAL', 'AUTHORITATIVE', '中共中央社会工作部', NULL, NULL, '2026-06-25', 'https://www.zyshgzb.gov.cn/n1/2026/0625/c462300-40747278.html'),
  ('党建引领新就业群体融入城市治理（学思践悟）', '权益保障', '理论文章总结以党建带动新就业群体融入城市治理的路径与机制。
发布方：中共中央社会工作部
发布日期：2026-02-27
适用范围：全国
原文链接：https://www.zyshgzb.gov.cn/n1/2026/0227/c461796-40671251.html', 0, 'PUBLISHED', 'OFFICIAL', 'AUTHORITATIVE', '中共中央社会工作部', NULL, NULL, '2026-02-27', 'https://www.zyshgzb.gov.cn/n1/2026/0227/c461796-40671251.html'),
  ('街巷奔跑者，城市守护者', '权益保障', '报道新就业群体参与城市治理并获得关爱服务的典型故事。
发布方：中共中央社会工作部
发布日期：2026-10-13
适用范围：全国
原文链接：https://www.zyshgzb.gov.cn/n1/2026/1013/c462733-40813531.html', 0, 'PUBLISHED', 'OFFICIAL', 'AUTHORITATIVE', '中共中央社会工作部', NULL, NULL, '2026-10-13', 'https://www.zyshgzb.gov.cn/n1/2026/1013/c462733-40813531.html'),
  ('我国将建立健全职业伤害保障制度', '职业伤害', '人社部门明确职业伤害保障制度将从试点走向全面建立。
发布方：中国就业网（人力资源社会保障部主管）
发布日期：2025-12-01
适用范围：全国
原文链接：https://chinajob.mohrss.gov.cn/h5/c/2025-12-01/466090.shtml', 0, 'PUBLISHED', 'OFFICIAL', 'AUTHORITATIVE', '中国就业网（人力资源社会保障部主管）', NULL, NULL, '2025-12-01', 'https://chinajob.mohrss.gov.cn/h5/c/2025-12-01/466090.shtml'),
  ('十八万个工会驿站汇聚暖流（青年圆桌·关注新就业群体）', '驿站服务', '全国工会驿站已达18万个，成为户外劳动者与新就业群体基础服务网络。
发布方：人民日报
发布日期：2025-02-06
适用范围：全国
原文链接：http://paper.people.com.cn/rmrb/pad/content/202502/06/content_30055037.html', 0, 'PUBLISHED', 'OFFICIAL', 'OFFICIAL_MEDIA', '人民日报', NULL, NULL, '2025-02-06', 'http://paper.people.com.cn/rmrb/pad/content/202502/06/content_30055037.html'),
  ('打出组合拳为网约车司机“撑腰”', '权益保障', '交通运输部门推出网约车司机权益保障组合措施，含抽成公开与申诉渠道。
发布方：新华网
发布日期：2022-05-18
适用范围：全国
原文链接：https://www.news.cn/politics/2022-05/18/c_1128659761.htm', 0, 'PUBLISHED', 'OFFICIAL', 'OFFICIAL_MEDIA', '新华网', NULL, NULL, '2022-05-18', 'https://www.news.cn/politics/2022-05/18/c_1128659761.htm'),
  ('上海市人力资源和社会保障局等九部门关于扩大新就业形态人员职业伤害保障试点工作的通知', '职业伤害', '上海落实国家扩围要求，明确本市职业伤害保障试点的参保与经办安排。
发布方：上海市人力资源和社会保障局等九部门
发布日期：2025-07-29
适用城市：上海市
原文链接：https://www.shanghai.gov.cn/rcjysj/20250924/dfcf444ca49548488ea2441894cdbfd6.html', 0, 'PUBLISHED', 'OFFICIAL', 'AUTHORITATIVE', '上海市人力资源和社会保障局等九部门', NULL, '310100', '2025-07-29', 'https://www.shanghai.gov.cn/rcjysj/20250924/dfcf444ca49548488ea2441894cdbfd6.html'),
  ('深圳市人民代表大会常务委员会关于加强新就业形态劳动者权益保障的决定', '权益保障', '深圳以人大决定形式立法保障新就业形态劳动者，涵盖报酬、算法与休息权。
发布方：深圳市人民代表大会常务委员会
发布日期：2025-11-17
适用城市：深圳市
原文链接：https://www.sz.gov.cn/zfgb/2025/gb1393/content/post_12505038.html', 0, 'PUBLISHED', 'OFFICIAL', 'AUTHORITATIVE', '深圳市人民代表大会常务委员会', NULL, '440300', '2025-11-17', 'https://www.sz.gov.cn/zfgb/2025/gb1393/content/post_12505038.html'),
  ('深圳出台新就业形态劳动者权益保障决定 为新就业群体构建法治化保障体系', '权益保障', '官方解读深圳人大决定的主要内容与法治化保障思路。
发布方：深圳市人民政府门户网站
发布日期：2025-11-17
适用城市：深圳市
原文链接：https://www.sz.gov.cn/ztfw/jylyfwzt/wyk_184808/content/mpost_12495987.html', 0, 'PUBLISHED', 'OFFICIAL', 'AUTHORITATIVE', '深圳市人民政府门户网站', NULL, '440300', '2025-11-17', 'https://www.sz.gov.cn/ztfw/jylyfwzt/wyk_184808/content/mpost_12495987.html'),
  ('市人大常委会出台加强新就业形态劳动者权益保障的决定 系列细致规定营造友好和谐劳动生态', '算法与规则', '报道深圳决定中关于算法考核、报酬规则等细化规定。
发布方：深圳市人民政府门户网站
发布日期：2025-10-31
适用城市：深圳市
原文链接：https://www.sz.gov.cn/cn/xxgk/zfxxgj/zwdt/content/mpost_12467472.html', 0, 'PUBLISHED', 'OFFICIAL', 'AUTHORITATIVE', '深圳市人民政府门户网站', NULL, '440300', '2025-10-31', 'https://www.sz.gov.cn/cn/xxgk/zfxxgj/zwdt/content/mpost_12467472.html'),
  ('考核指标不得损害劳动者安全健康', '算法与规则', '全国人大网站报道地方立法明确平台考核指标不得危害劳动者安全健康。
发布方：中国人大网
发布日期：2025-11-25
适用范围：全国
原文链接：http://www.npc.gov.cn/c2/c30834/202511/t20251125_449675.html', 0, 'PUBLISHED', 'OFFICIAL', 'AUTHORITATIVE', '中国人大网', NULL, NULL, '2025-11-25', 'http://www.npc.gov.cn/c2/c30834/202511/t20251125_449675.html'),
  ('深圳为170万新就业形态劳动者构筑制度保障', '权益保障', '广东省政府网报道深圳为约170万新就业形态劳动者建立制度保障体系。
发布方：广东省人民政府门户网站
发布日期：2026-02-26
适用城市：深圳市
原文链接：http://www.gd.gov.cn/gdywdt/dsdt/content/post_4860071.html', 0, 'PUBLISHED', 'OFFICIAL', 'AUTHORITATIVE', '广东省人民政府门户网站', NULL, '440300', '2026-02-26', 'http://www.gd.gov.cn/gdywdt/dsdt/content/post_4860071.html'),
  ('深圳网点站点与属地社区结对共建工作指引出台', '驿站服务', '深圳出台工作指引，推动快递网点站点与属地社区结对，融入基层服务网络。
发布方：国家邮政局
发布日期：2026-05-21
适用城市：深圳市
原文链接：https://www.spb.gov.cn/gjyzj/c100015/c100017/202605/ca59f1b4012e4d989ce4c8fb47eb89e6.shtml', 0, 'PUBLISHED', 'OFFICIAL', 'AUTHORITATIVE', '国家邮政局', NULL, '440300', '2026-05-21', 'https://www.spb.gov.cn/gjyzj/c100015/c100017/202605/ca59f1b4012e4d989ce4c8fb47eb89e6.shtml'),
  ('把“新”声放心里，变诉求为福祉', '权益保障', '深圳龙华建立新就业群体诉求收集与办理机制，把诉求转化为具体福祉。
发布方：深圳市龙华区人民政府
发布日期：2026-07-27
适用城市：深圳市
原文链接：https://www.szlhq.gov.cn/bmxxgk/qtgzb/dtxx_124446/gzdt_124447/content/post_12910312.html', 0, 'PUBLISHED', 'OFFICIAL', 'AUTHORITATIVE', '深圳市龙华区人民政府', NULL, '440300', '2026-07-27', 'https://www.szlhq.gov.cn/bmxxgk/qtgzb/dtxx_124446/gzdt_124447/content/post_12910312.html'),
  ('关于印发杭州市维护新就业形态劳动者劳动保障权益实施办法（试行）的通知', '权益保障', '杭州出台新就业形态劳动者权益保障实施办法，细化用工与报酬规则。
发布方：杭州市人民政府
发布日期：2022-05-12
适用城市：杭州市
原文链接：http://www.hangzhou.gov.cn/art/2022/5/12/art_1229063383_1815758.html', 0, 'PUBLISHED', 'OFFICIAL', 'AUTHORITATIVE', '杭州市人民政府', NULL, '330100', '2022-05-12', 'http://www.hangzhou.gov.cn/art/2022/5/12/art_1229063383_1815758.html'),
  ('“爱心驿家”为新就业群体筑起温馨家', '驿站服务', '杭州以“爱心驿家”为品牌建设新就业群体服务阵地并提供关爱服务。
发布方：杭州市人民政府
发布日期：2026-01-26
适用城市：杭州市
原文链接：https://www.hangzhou.gov.cn/col/col812270/art/2026/art_3cc85d4e0de14d0bb700c125b944e80a.html', 0, 'PUBLISHED', 'OFFICIAL', 'AUTHORITATIVE', '杭州市人民政府', NULL, '330100', '2026-01-26', 'https://www.hangzhou.gov.cn/col/col812270/art/2026/art_3cc85d4e0de14d0bb700c125b944e80a.html'),
  ('杭州新增一处“小哥友好街区”', '驿站服务', '杭州新增“小哥友好街区”，以街区为单位集中提供歇脚与关爱服务。
发布方：浙江省总工会（浙江网上职工之家）
发布日期：2026-07-30
适用城市：杭州市
原文链接：https://zjftu.org/page/zj_zgh/zj_xwzx/zj_xwzx_jcdt/2026-07-30/6924873431404591.html', 0, 'PUBLISHED', 'OFFICIAL', 'SECONDARY', '浙江省总工会（浙江网上职工之家）', NULL, '330100', '2026-07-30', 'https://zjftu.org/page/zj_zgh/zj_xwzx/zj_xwzx_jcdt/2026-07-30/6924873431404591.html'),
  ('精准对接骑手需求、探索融入城市基层治理新路径 浙江省杭州市拱墅区这样做', '驿站服务', '杭州拱墅按骑手需求配置服务，探索骑手融入城市基层治理的路径。
发布方：中工网（工人日报）
发布日期：2026-08-01
适用城市：杭州市
原文链接：https://www.workercn.cn/c/2026-08-01/8860829.shtml', 0, 'PUBLISHED', 'OFFICIAL', 'OFFICIAL_MEDIA', '中工网（工人日报）', NULL, '330100', '2026-08-01', 'https://www.workercn.cn/c/2026-08-01/8860829.shtml'),
  ('上城打造新就业群体友好城区样板', '驿站服务', '杭州上城区建设新就业群体友好城区，形成阵地服务与诉求响应样板。
发布方：浙江省人民政府门户网站
发布日期：2025-11-06
适用城市：杭州市
原文链接：https://www.zj.gov.cn/col/col1554469/art/2025/art_f680634f3d12f865cadfb954b36cda2e.html', 0, 'PUBLISHED', 'OFFICIAL', 'AUTHORITATIVE', '浙江省人民政府门户网站', NULL, '330100', '2025-11-06', 'https://www.zj.gov.cn/col/col1554469/art/2025/art_f680634f3d12f865cadfb954b36cda2e.html'),
  ('西湖区共建共享全域友好社会', '驿站服务', '杭州西湖区推进全域友好社会建设，落地面向新就业群体的友好场景。
发布方：浙江省人民政府门户网站
发布日期：2026-01-29
适用城市：杭州市
原文链接：https://www.zj.gov.cn/col/col1554469/art/2026/art_b88577a04e5c4ef970969c619ec42671.html', 0, 'PUBLISHED', 'OFFICIAL', 'AUTHORITATIVE', '浙江省人民政府门户网站', NULL, '330100', '2026-01-29', 'https://www.zj.gov.cn/col/col1554469/art/2026/art_b88577a04e5c4ef970969c619ec42671.html'),
  ('用心用情打造“小哥之家”——浙江省杭州市全方位关爱新就业群体', '驿站服务', '杭州建设“小哥之家”等服务阵地，为骑手提供歇脚、充电等关爱服务。
发布方：人民网
发布日期：2024-11-14
适用城市：杭州市
原文链接：http://society.people.com.cn/n1/2024/1114/c1008-40360601.html', 0, 'PUBLISHED', 'OFFICIAL', 'OFFICIAL_MEDIA', '人民网', NULL, '330100', '2024-11-14', 'http://society.people.com.cn/n1/2024/1114/c1008-40360601.html'),
  ('杭州市人力资源和社会保障局关于市十四届人大六次会议拱墅23号建议的答复', '社会保险', '杭州人社部门答复人大建议，说明新就业形态劳动者参保等保障进展。
发布方：杭州市人力资源和社会保障局
发布日期：2026-08-04
适用城市：杭州市
原文链接：https://hrss.hangzhou.gov.cn/col/col1229578408/art/2026/art_768afdf263eea3995ff3f7e444e7ae09.html', 0, 'PUBLISHED', 'OFFICIAL', 'AUTHORITATIVE', '杭州市人力资源和社会保障局', NULL, '330100', '2026-08-04', 'https://hrss.hangzhou.gov.cn/col/col1229578408/art/2026/art_768afdf263eea3995ff3f7e444e7ae09.html'),
  ('北京新就业群体服务升级', '驿站服务', '北京推动新就业群体服务升级，拓展驿站与关爱服务供给。
发布方：人民网北京频道
发布日期：2025-03-22
适用城市：北京市
原文链接：http://bj.people.com.cn/n2/2025/0322/c14540-41171993.html', 0, 'PUBLISHED', 'OFFICIAL', 'OFFICIAL_MEDIA', '人民网北京频道', NULL, '110100', '2025-03-22', 'http://bj.people.com.cn/n2/2025/0322/c14540-41171993.html'),
  ('友好之城·温暖同行 为首都友好之城故事注入双井“新”声', '驿站服务', '北京朝阳双井街道推进新就业群体友好场景建设与关爱服务。
发布方：北京市朝阳区人民政府
发布日期：2026-01-26
适用城市：北京市
原文链接：http://www.bjchy.gov.cn/dynamic/jxdt/4028805a9bded2a3019bf99843be14fd.html', 0, 'PUBLISHED', 'OFFICIAL', 'AUTHORITATIVE', '北京市朝阳区人民政府', NULL, '110100', '2026-01-26', 'http://www.bjchy.gov.cn/dynamic/jxdt/4028805a9bded2a3019bf99843be14fd.html'),
  ('北京：将出台平台经济吸纳就业指导意见', '权益保障', '北京拟出台平台经济吸纳就业指导意见，规范平台用工与就业服务。
发布方：中国就业网（人力资源社会保障部主管）
发布日期：2026-01-23
适用城市：北京市
原文链接：https://chinajob.mohrss.gov.cn/h5/c/2026-01-23/484167.shtml', 0, 'PUBLISHED', 'OFFICIAL', 'AUTHORITATIVE', '中国就业网（人力资源社会保障部主管）', NULL, '110100', '2026-01-23', 'https://chinajob.mohrss.gov.cn/h5/c/2026-01-23/484167.shtml'),
  ('北京170万新就业形态劳动者纳入职业伤害保障，出行行业全覆盖', '职业伤害', '北京约170万新就业形态劳动者纳入职业伤害保障，出行行业实现全覆盖。
发布方：新京报
发布日期：2026-01-01
适用城市：北京市
原文链接：https://m.bjnews.com.cn/detail/1774875416129043.html', 0, 'PUBLISHED', 'OFFICIAL', 'SECONDARY', '新京报', NULL, '110100', '2026-01-01', 'https://m.bjnews.com.cn/detail/1774875416129043.html'),
  ('上海新就业“全域友好”可知可感——建立全域友好服务场景 探索全方位就业保障机制', '驿站服务', '上海建设全域友好服务场景与就业保障机制，让新就业群体服务可知可感。
发布方：上海市人民政府门户网站
发布日期：2026-05-05
适用城市：上海市
原文链接：https://www.shanghai.gov.cn/nw4411/20260505/0588fe03d31d492e9556890406393e2d.html', 0, 'PUBLISHED', 'OFFICIAL', 'AUTHORITATIVE', '上海市人民政府门户网站', NULL, '310100', '2026-05-05', 'https://www.shanghai.gov.cn/nw4411/20260505/0588fe03d31d492e9556890406393e2d.html'),
  ('从全家驿站到算法优化……普陀把小哥的急难愁盼，带上全国两会', '算法与规则', '上海普陀从驿站服务到算法优化收集骑手诉求，并向上反映至全国两会。
发布方：上海市人民政府门户网站
发布日期：2026-03-10
适用城市：上海市
原文链接：https://www.shanghai.gov.cn/nw15343/20260311/f831140ed79a402a91ca0660b3c1fc3d.html', 0, 'PUBLISHED', 'OFFICIAL', 'AUTHORITATIVE', '上海市人民政府门户网站', NULL, '310100', '2026-03-10', 'https://www.shanghai.gov.cn/nw15343/20260311/f831140ed79a402a91ca0660b3c1fc3d.html'),
  ('一场“双向奔赴”的调研 杨浦如何让“新”力量更有奔头', '权益保障', '上海杨浦通过调研梳理新就业群体需求，完善服务与保障举措。
发布方：上海市杨浦区人民政府（上海市政府网站发布）
发布日期：2026-04-27
适用城市：上海市
原文链接：https://www.shanghai.gov.cn/nw15343/20260428/f32f79efb8294a9bb9d26779e27d5884.html', 0, 'PUBLISHED', 'OFFICIAL', 'AUTHORITATIVE', '上海市杨浦区人民政府（上海市政府网站发布）', NULL, '310100', '2026-04-27', 'https://www.shanghai.gov.cn/nw15343/20260428/f32f79efb8294a9bb9d26779e27d5884.html'),
  ('上海：让新就业群体留得下、住得安、能成业', '权益保障', '上海围绕居住、就业与融入推出面向新就业群体的服务保障安排。
发布方：中共中央社会工作部
发布日期：2026-04-13
适用城市：上海市
原文链接：http://www.zyshgzb.gov.cn/n1/2026/0413/c462100-40700215.html', 0, 'PUBLISHED', 'OFFICIAL', 'AUTHORITATIVE', '中共中央社会工作部', NULL, '310100', '2026-04-13', 'http://www.zyshgzb.gov.cn/n1/2026/0413/c462100-40700215.html'),
  ('关于加强灵活就业和新就业形态法治保障的议案', '权益保障', '上海市人大代表提出加强灵活就业与新就业形态法治保障的议案。
发布方：上海市人民代表大会
发布日期：2025-01-01
适用城市：上海市
原文链接：https://www.shrd.gov.cn/shrd/16j2c/content/0baa826a-3bb9-46c1-be03-7210be9cb3f0.html', 0, 'PUBLISHED', 'OFFICIAL', 'AUTHORITATIVE', '上海市人民代表大会', NULL, '310100', '2025-01-01', 'https://www.shrd.gov.cn/shrd/16j2c/content/0baa826a-3bb9-46c1-be03-7210be9cb3f0.html'),
  ('全域友好 全城有爱 广州探索超大城市新就业群体服务管理有效路径', '驿站服务', '广州以“全域友好”思路探索超大城市新就业群体服务管理路径。
发布方：中共中央社会工作部
发布日期：2025-11-28
适用城市：广州市
原文链接：https://www.zyshgzb.gov.cn/n1/2025/1128/c461321-40613672.html', 0, 'PUBLISHED', 'OFFICIAL', 'AUTHORITATIVE', '中共中央社会工作部', NULL, '440100', '2025-11-28', 'https://www.zyshgzb.gov.cn/n1/2025/1128/c461321-40613672.html'),
  ('广州荔湾：实施“凝‘新’聚荔”党旗红惠民工程 织密新就业群体“幸福网”', '驿站服务', '广州荔湾以党建惠民工程织密新就业群体服务网络与关爱举措。
发布方：中共广东省委组织部
发布日期：2025-12-04
适用城市：广州市
原文链接：https://www.gdzz.gov.cn/zzjs/jcdjc/content/post_24316.html', 0, 'PUBLISHED', 'OFFICIAL', 'AUTHORITATIVE', '中共广东省委组织部', NULL, '440100', '2025-12-04', 'https://www.gdzz.gov.cn/zzjs/jcdjc/content/post_24316.html'),
  ('广东广州组建新业态用工保障联盟：打造暖“新”幸福圈', '权益保障', '广州组建新业态用工保障联盟，联合多方为新就业群体提供保障服务。
发布方：共产党员网（中共中央组织部）
发布日期：2025-02-19
适用城市：广州市
原文链接：https://www.dangjian.cn/qydj/2025/02/19/detail_202502197008477.html', 0, 'PUBLISHED', 'OFFICIAL', 'SECONDARY', '共产党员网（中共中央组织部）', NULL, '440100', '2025-02-19', 'https://www.dangjian.cn/qydj/2025/02/19/detail_202502197008477.html'),
  ('广州：“小个专”，出“骑”效', '权益保障', '市场监管总局介绍广州以党建带动骑手群体服务与管理的做法。
发布方：国家市场监督管理总局
发布日期：2025-11-06
适用城市：广州市
原文链接：https://xgz.samr.gov.cn/gzjl/zdjs/art/2025/art_4764aaed52894c758b0a46d857dc3130.html', 0, 'PUBLISHED', 'OFFICIAL', 'AUTHORITATIVE', '国家市场监督管理总局', NULL, '440100', '2025-11-06', 'https://xgz.samr.gov.cn/gzjl/zdjs/art/2025/art_4764aaed52894c758b0a46d857dc3130.html'),
  ('护航城市奔跑者，成都政策“服务包”来了', '权益保障', '成都推出面向新就业群体的政策“服务包”，集成多项服务保障。
发布方：人民网四川频道
发布日期：2026-02-12
适用城市：成都市
原文链接：http://sc.people.com.cn/n2/2026/0212/c345167-41500740.html', 0, 'PUBLISHED', 'OFFICIAL', 'OFFICIAL_MEDIA', '人民网四川频道', NULL, '510100', '2026-02-12', 'http://sc.people.com.cn/n2/2026/0212/c345167-41500740.html'),
  ('关于做好新就业形态人员职业伤害保障业务经办和征收管理相关工作的通知', '职业伤害', '成都明确职业伤害保障的业务经办与费用征收管理衔接办法。
发布方：成都市中小企业网（转载成都市人社、税务部门通知）
发布日期：2026-01-01
适用城市：成都市
原文链接：https://cdsme.org/page.aspx?id=22073&classid=173', 0, 'PUBLISHED', 'OFFICIAL', 'SECONDARY', '成都市中小企业网（转载成都市人社、税务部门通知）', NULL, '510100', '2026-01-01', 'https://cdsme.org/page.aspx?id=22073&classid=173'),
  ('新一批爱心驿站点位发布，让户外工作者“有家有依靠”', '驿站服务', '成都发布新一批爱心驿站点位，扩大户外工作者服务覆盖。
发布方：成都商报（红星新闻）
发布日期：2026-07-01
适用城市：成都市
原文链接：https://static.cdsb.com/micropub/Articles/202607/c93652744b8a1aabb1c894d1c147bb65.html', 0, 'PUBLISHED', 'OFFICIAL', 'SECONDARY', '成都商报（红星新闻）', NULL, '510100', '2026-07-01', 'https://static.cdsb.com/micropub/Articles/202607/c93652744b8a1aabb1c894d1c147bb65.html'),
  ('湖北武汉：武昌区工会驿站跑出工伤救助“加速度”', '职业伤害', '武汉武昌区工会驿站协助新就业群体快速办理工伤救助。
发布方：中国就业网（人力资源社会保障部主管）
发布日期：2026-04-24
适用城市：武汉市
原文链接：https://chinajob.mohrss.gov.cn/c/2026-04-24/532050.shtml', 0, 'PUBLISHED', 'OFFICIAL', 'AUTHORITATIVE', '中国就业网（人力资源社会保障部主管）', NULL, '420100', '2026-04-24', 'https://chinajob.mohrss.gov.cn/c/2026-04-24/532050.shtml'),
  ('武汉江夏：手绘地图解锁新就业群体“友好场景”', '驿站服务', '武汉江夏用手绘友好地图标注服务场景，方便新就业群体就近获取服务。
发布方：新华网湖北频道
发布日期：2026-01-10
适用城市：武汉市
原文链接：http://www.hubei.xinhua.org/20260110/b08e160d439d48bfbc738caec3f42213/c.html', 0, 'PUBLISHED', 'OFFICIAL', 'SECONDARY', '新华网湖北频道', NULL, '420100', '2026-01-10', 'http://www.hubei.xinhua.org/20260110/b08e160d439d48bfbc738caec3f42213/c.html'),
  ('驿站有温度，服务有真情！江岸区多部门春节前联合走访慰问新就业群体', '驿站服务', '武汉江岸区多部门联合走访慰问新就业群体，提升驿站服务温度。
发布方：极目新闻（楚天都市报）
发布日期：2026-02-01
适用城市：武汉市
原文链接：https://www.ctdsb.net/c1734_202602/2661061.html', 0, 'PUBLISHED', 'OFFICIAL', 'SECONDARY', '极目新闻（楚天都市报）', NULL, '420100', '2026-02-01', 'https://www.ctdsb.net/c1734_202602/2661061.html'),
  ('以心暖“新” 凝“新”聚力——新就业群体服务管理“友好场景”建设（“六个壹起”暖心 “墩墩壹家”同行）', '驿站服务', '介绍基层以“友好场景”为抓手整合资源、服务新就业群体的做法。
发布方：中共中央社会工作部
发布日期：2026-02-02
适用范围：全国
原文链接：https://www.zyshgzb.gov.cn/n1/2026/0202/c461657-40657889.html', 0, 'PUBLISHED', 'OFFICIAL', 'AUTHORITATIVE', '中共中央社会工作部', NULL, NULL, '2026-02-02', 'https://www.zyshgzb.gov.cn/n1/2026/0202/c461657-40657889.html'),
  ('外卖平台保障夏季跑单：高温关怀金随单发放，300万骑手免费上防暑险', '福利补贴', '美团夏季为骑手随单发放高温关怀金，并为300万骑手免费提供防暑险。
发布方：美团
发布日期：2025-06-26
适用范围：全国
原文链接：https://www.meituan.com/news/NN250626087004590', 0, 'PUBLISHED', 'PLATFORM', 'SECONDARY', '美团', '美团', NULL, '2025-06-26', 'https://www.meituan.com/news/NN250626087004590'),
  ('第十次算法公开：2025年底前全面取消超时扣款', '算法与规则', '美团第十次公开算法，承诺2025年底前全面取消骑手超时扣款。
发布方：美团
发布日期：2025-08-27
适用范围：全国
原文链接：https://www.meituan.com/zh-HK/news/NN250827113003555', 0, 'PUBLISHED', 'PLATFORM', 'SECONDARY', '美团', '美团', NULL, '2025-08-27', 'https://www.meituan.com/zh-HK/news/NN250827113003555'),
  ('美团启动2026年“骑手关怀大篷车”巡游，为百万骑手提供夏季综合保障', '福利补贴', '美团启动骑手关怀大篷车巡游，为百万骑手提供夏季综合保障服务。
发布方：美团
发布日期：2026-07-14
适用范围：全国
原文链接：https://www.meituan.com/news/NN260714181010536', 0, 'PUBLISHED', 'PLATFORM', 'SECONDARY', '美团', '美团', NULL, '2026-07-14', 'https://www.meituan.com/news/NN260714181010536'),
  ('美团获评“全国就业与社会保障先进民营企业”', '权益保障', '美团公告其获评全国就业与社会保障先进民营企业，并披露骑手保障投入。
发布方：美团
发布日期：2026-01-08
适用范围：全国
原文链接：https://www.meituan.com/news/NN260108200002211', 0, 'PUBLISHED', 'PLATFORM', 'SECONDARY', '美团', '美团', NULL, '2026-01-08', 'https://www.meituan.com/news/NN260108200002211'),
  ('美团未来五年投入100亿完善骑手保障，推出骑手公寓、拓展大病关怀', '权益保障', '美团宣布五年投入100亿元完善骑手保障，新增骑手公寓与大病关怀。
发布方：新华网（报道美团官方发布）
发布日期：2025-11-22
适用范围：全国
原文链接：http://www.news.cn/tech/20251122/818c27d08b17459eba30508aa1038b6c/c.html', 0, 'PUBLISHED', 'OFFICIAL', 'OFFICIAL_MEDIA', '新华网（报道美团官方发布）', '美团', NULL, '2025-11-22', 'http://www.news.cn/tech/20251122/818c27d08b17459eba30508aa1038b6c/c.html'),
  ('美团京东双双官宣为骑手缴纳社保', '社会保险', '美团与京东同日宣布为骑手缴纳社保，新就业形态社保覆盖取得突破。
发布方：中工网（工人日报）
发布日期：2025-02-21
适用范围：全国
原文链接：https://www.workercn.cn/c/2025-02-21/8461616.shtml', 0, 'PUBLISHED', 'OFFICIAL', 'OFFICIAL_MEDIA', '中工网（工人日报）', '美团', NULL, '2025-02-21', 'https://www.workercn.cn/c/2025-02-21/8461616.shtml'),
  ('2026年度美团劳动规则和算法协商恳谈会召开', '算法与规则', '美团与工会召开年度劳动规则与算法协商恳谈会，扩大“红灯停表”试点。
发布方：陕工网（陕西工人报）
发布日期：2026-10-08
适用范围：全国
原文链接：https://www.sxworker.com/xinwen/gn/2026-10-08/224411.html', 0, 'PUBLISHED', 'OFFICIAL', 'SECONDARY', '陕工网（陕西工人报）', '美团', NULL, '2026-10-08', 'https://www.sxworker.com/xinwen/gn/2026-10-08/224411.html'),
  ('让“算法”成为骑手建设美好生活的“办法”，饿了么推出九项举措', '算法与规则', '饿了么推出九项举措改进骑手算法与劳动规则，回应骑手权益关切。
发布方：界面新闻
发布日期：2025-01-01
适用范围：全国
原文链接：https://www.jiemian.com/article/12277178.html', 0, 'PUBLISHED', 'OFFICIAL', 'SECONDARY', '界面新闻', '饿了么', NULL, '2025-01-01', 'https://www.jiemian.com/article/12277178.html'),
  ('饿了么：逐步取消超时罚款，每日配送不超过11小时', '算法与规则', '饿了么宣布逐步取消超时罚款，并设定每日配送不超过11小时的防疲劳上限。
发布方：北京日报客户端
发布日期：2025-04-25
适用范围：全国
原文链接：https://xinwen.bjd.com.cn/content/s680b7e6ae4b0ec1c3d964614.html', 0, 'PUBLISHED', 'OFFICIAL', 'SECONDARY', '北京日报客户端', '饿了么', NULL, '2025-04-25', 'https://xinwen.bjd.com.cn/content/s680b7e6ae4b0ec1c3d964614.html'),
  ('聚焦新就业形态劳动者｜多家平台取消超时罚款并上线“防疲劳”机制', '算法与规则', '多家外卖平台取消超时罚款并上线防疲劳机制，骑手算法规则持续调整。
发布方：中工网（工人日报）
发布日期：2026-05-15
适用范围：全国
原文链接：https://www.workercn.cn/c/2026-05-15/8801752.shtml', 0, 'PUBLISHED', 'OFFICIAL', 'OFFICIAL_MEDIA', '中工网（工人日报）', '饿了么', NULL, '2026-05-15', 'https://www.workercn.cn/c/2026-05-15/8801752.shtml'),
  ('骑手升级“城市骑士”！饿了么淘宝闪购全面提升骑士保障激励体系', '权益保障', '饿了么与淘宝闪购升级骑士保障激励体系，将骑手升级为“城市骑士”。
发布方：中国网科技
发布日期：2025-08-17
适用范围：全国
原文链接：http://tech.china.com.cn/sx/20250817/410131.shtml', 0, 'PUBLISHED', 'OFFICIAL', 'SECONDARY', '中国网科技', '饿了么', NULL, '2025-08-17', 'http://tech.china.com.cn/sx/20250817/410131.shtml'),
  ('【算法协商 技术向善】骑士社保补贴实现全国覆盖', '社会保险', '经算法协商，骑士社保补贴实现全国覆盖，平台社保责任进一步落地。
发布方：工人日报（中工网）
发布日期：2026-05-06
适用范围：全国
原文链接：https://www.workercn.cn/papers/grrb/2026/05/06/3/news-6.html', 0, 'PUBLISHED', 'OFFICIAL', 'OFFICIAL_MEDIA', '工人日报（中工网）', '饿了么', NULL, '2026-05-06', 'https://www.workercn.cn/papers/grrb/2026/05/06/3/news-6.html'),
  ('T3出行上线医疗、意外综合险 持续加码网约车司机关怀', '福利补贴', 'T3出行为司机上线医疗与意外综合险，并在春运期间发放司机保险保障。
发布方：央广网
发布日期：2026-02-11
适用范围：全国
原文链接：https://news.cnr.cn/native/gd/20260211/t20260211_527523403.shtml', 0, 'PUBLISHED', 'OFFICIAL', 'OFFICIAL_MEDIA', '央广网', 'T3出行', NULL, '2026-02-11', 'https://news.cnr.cn/native/gd/20260211/t20260211_527523403.shtml'),
  ('开展算法协商 保障网约车司机民主权益（T3出行达成全网平台算法和劳动保障集体协议）', '算法与规则', 'T3出行达成全网平台算法与劳动保障集体协议，经协商确定算法规则。
发布方：中工网（工人日报）
发布日期：2025-07-18
适用范围：全国
原文链接：https://www.workercn.cn/c/2025-07-18/8564723.shtml', 0, 'PUBLISHED', 'OFFICIAL', 'OFFICIAL_MEDIA', '中工网（工人日报）', 'T3出行', NULL, '2025-07-18', 'https://www.workercn.cn/c/2025-07-18/8564723.shtml'),
  ('最高27% 多家网约车平台下调抽成比例', '劳动报酬', '多家网约车平台下调抽成比例，最高抽成上限压至27%。
发布方：中工网（工人日报）
发布日期：2025-08-23
适用范围：全国
原文链接：https://www.workercn.cn/c/2025-08-23/8586355.shtml', 0, 'PUBLISHED', 'OFFICIAL', 'OFFICIAL_MEDIA', '中工网（工人日报）', '滴滴出行', NULL, '2025-08-23', 'https://www.workercn.cn/c/2025-08-23/8586355.shtml'),
  ('协商成果推动滴滴平台账单透明化、多维降抽成 网约车司机抽成有本“明白账”', '劳动报酬', '滴滴经集体协商推动账单透明化并多维度降低抽成，司机收入更可核对。
发布方：云南网
发布日期：2026-05-31
适用范围：全国
原文链接：http://news.yunnan.cn/system/2026/05/31/034029517.shtml', 0, 'PUBLISHED', 'OFFICIAL', 'SECONDARY', '云南网', '滴滴出行', NULL, '2026-05-31', 'http://news.yunnan.cn/system/2026/05/31/034029517.shtml'),
  ('落实新业态劳动者权益保障，滴滴推出“橙意保障计划”', '权益保障', '滴滴推出“橙意保障计划”，为网约车司机提供权益与保障安排。
发布方：中国新闻网
发布日期：2023-06-16
适用范围：全国
原文链接：https://www.chinanews.com.cn/cj/2023/06-16/10026016.shtml', 0, 'PUBLISHED', 'OFFICIAL', 'OFFICIAL_MEDIA', '中国新闻网', '滴滴出行', NULL, '2023-06-16', 'https://www.chinanews.com.cn/cj/2023/06-16/10026016.shtml'),
  ('出行平台高德打车限制抽佣 宣布平台信息服务费上限降至9%', '劳动报酬', '高德打车宣布平台信息服务费上限降至9%，限制对司机的抽佣水平。
发布方：网经社（电子商务研究中心）
发布日期：2025-01-01
适用范围：全国
原文链接：http://100ec.cn/detail--6651674.html', 0, 'PUBLISHED', 'OFFICIAL', 'SECONDARY', '网经社（电子商务研究中心）', '高德打车', NULL, '2025-01-01', 'http://100ec.cn/detail--6651674.html'),
  ('多家网约车宣布：降低抽成！', '劳动报酬', '经济日报报道多家网约车平台宣布降低抽成，司机收入保障受到关注。
发布方：经济日报
发布日期：2025-08-22
适用范围：全国
原文链接：http://www.jingjiribao.cn/static/detail.jsp?id=605086', 0, 'PUBLISHED', 'OFFICIAL', 'OFFICIAL_MEDIA', '经济日报', NULL, NULL, '2025-08-22', 'http://www.jingjiribao.cn/static/detail.jsp?id=605086'),
  ('多家网约车、货运平台优化收入抽成算法', '算法与规则', '新华社报道网约车与货运平台优化抽成算法，提高收入分配透明度。
发布方：经济参考报（新华社）
发布日期：2026-05-09
适用范围：全国
原文链接：http://jjckb.xinhuanet.com/20260509/4c9ad89855514788b0aae8efcc2fe26d/c.html', 0, 'PUBLISHED', 'OFFICIAL', 'OFFICIAL_MEDIA', '经济参考报（新华社）', NULL, NULL, '2026-05-09', 'http://jjckb.xinhuanet.com/20260509/4c9ad89855514788b0aae8efcc2fe26d/c.html'),
  ('市场监管总局督导货拉拉落实反垄断合规整改', '劳动报酬', '市场监管总局督导货拉拉整改，抽成费率降至约9%并退还不合理费用。
发布方：央视网经济频道
发布日期：2026-06-18
适用范围：全国
原文链接：https://jingji.cctv.com/2026/06/18/ARTIL2yqZlyt6RWsT0vw4LX1260618.shtml', 0, 'PUBLISHED', 'OFFICIAL', 'OFFICIAL_MEDIA', '央视网经济频道', '货拉拉', NULL, '2026-06-18', 'https://jingji.cctv.com/2026/06/18/ARTIL2yqZlyt6RWsT0vw4LX1260618.shtml'),
  ('货拉拉回应整改：已取消强制车贴，降低平台费率', '劳动报酬', '货拉拉回应监管整改，取消强制车贴并下调平台费率与不合理收费。
发布方：中国青年网
发布日期：2026-06-18
适用范围：全国
原文链接：https://news.youth.cn/jsxw/202606/t20260618_16721482.htm', 0, 'PUBLISHED', 'OFFICIAL', 'OFFICIAL_MEDIA', '中国青年网', '货拉拉', NULL, '2026-06-18', 'https://news.youth.cn/jsxw/202606/t20260618_16721482.htm'),
  ('运费保障升级、新增“司机之家”……满帮司机恳谈会达成多项共识', '驿站服务', '满帮司机恳谈会达成多项共识，升级运费保障并新增“司机之家”。
发布方：中国交通新闻网（中国交通报社）
发布日期：2026-03-27
适用范围：全国
原文链接：https://www.zgjtb.com/2026-03/27/content_513339.html', 0, 'PUBLISHED', 'OFFICIAL', 'SECONDARY', '中国交通新闻网（中国交通报社）', '满帮', NULL, '2026-03-27', 'https://www.zgjtb.com/2026-03/27/content_513339.html'),
  ('满帮恳谈会达成多项共识', '劳动报酬', '满帮与货车司机恳谈形成共识，涉及运费保障与平台规则优化。
发布方：中工网（工人日报）
发布日期：2026-03-28
适用范围：全国
原文链接：https://www.workercn.cn/c/2026-03-28/8767141.shtml', 0, 'PUBLISHED', 'OFFICIAL', 'OFFICIAL_MEDIA', '中工网（工人日报）', '满帮', NULL, '2026-03-28', 'https://www.workercn.cn/c/2026-03-28/8767141.shtml'),
  ('协商恳谈保障货车司机权益', '权益保障', '报道以协商恳谈机制保障货车司机权益的做法与进展。
发布方：中工网（工人日报）
发布日期：2026-04-03
适用范围：全国
原文链接：https://www.workercn.cn/c/2026-04-03/8771727.shtml', 0, 'PUBLISHED', 'OFFICIAL', 'OFFICIAL_MEDIA', '中工网（工人日报）', NULL, NULL, '2026-04-03', 'https://www.workercn.cn/c/2026-04-03/8771727.shtml'),
  ('京东宣布：承担骑手五险一金所有成本', '社会保险', '京东宣布为全职骑手承担五险一金全部成本，推进骑手社保覆盖。
发布方：新浪财经（报道京东官方宣布）
发布日期：2025-02-24
适用范围：全国
原文链接：https://finance.sina.com.cn/wm/2025-02-24/doc-inemqyfa9811778.shtml', 0, 'PUBLISHED', 'OFFICIAL', 'SECONDARY', '新浪财经（报道京东官方宣布）', '京东外卖', NULL, '2025-02-24', 'https://finance.sina.com.cn/wm/2025-02-24/doc-inemqyfa9811778.shtml'),
  ('京东未来三月招10万全职骑手，首批全面覆盖130多个城市', '权益保障', '京东宣布三个月招聘10万名全职骑手，首批覆盖130多个城市并配套保障。
发布方：新浪财经
发布日期：2025-04-27
适用范围：全国
原文链接：https://finance.sina.com.cn/jjxw/2025-04-27/doc-ineurezv7470281.shtml', 0, 'PUBLISHED', 'OFFICIAL', 'SECONDARY', '新浪财经', '京东外卖', NULL, '2025-04-27', 'https://finance.sina.com.cn/jjxw/2025-04-27/doc-ineurezv7470281.shtml'),
  ('刘强东请15万全职骑手吃肯德基 京东外卖联合百大品牌“11.11请客吃饭”', '福利补贴', '京东外卖为15万全职骑手提供餐饮福利，属骑手关爱类企业举措。
发布方：新华网
发布日期：2025-10-30
适用范围：全国
原文链接：https://www.news.cn/enterprise/20251030/81fd975c99504fc1b8297dc429a87792/c.html', 0, 'PUBLISHED', 'OFFICIAL', 'OFFICIAL_MEDIA', '新华网', '京东外卖', NULL, '2025-10-30', 'https://www.news.cn/enterprise/20251030/81fd975c99504fc1b8297dc429a87792/c.html'),
  ('年夜饭、丰暖礼包、关怀基金，顺丰同城累计投入5.3亿守护骑士温暖前行', '福利补贴', '顺丰同城累计投入5.3亿元，通过年夜饭、礼包与关怀基金保障骑士。
发布方：中国网海丝频道
发布日期：2026-02-12
适用范围：全国
原文链接：http://hs.china.com.cn/2026-02/12/content_43356191.html', 0, 'PUBLISHED', 'OFFICIAL', 'SECONDARY', '中国网海丝频道', '顺丰同城', NULL, '2026-02-12', 'http://hs.china.com.cn/2026-02/12/content_43356191.html'),
  ('顺丰集团签订集体合同补充协议明确薪酬与算法考核规则', '算法与规则', '顺丰通过集体合同补充协议明确快递员薪酬与算法考核规则。
发布方：广东省总工会
发布日期：2026-01-01
适用范围：全国
原文链接：https://www.gdftu.org.cn/xwzx/jcld/content/post_1336332.html', 0, 'PUBLISHED', 'OFFICIAL', 'SECONDARY', '广东省总工会', '顺丰', NULL, '2026-01-01', 'https://www.gdftu.org.cn/xwzx/jcld/content/post_1336332.html'),
  ('第四个“中通小哥日”：全面推进小哥权益保障', '权益保障', '中通在小哥日发布全面推进快递员权益保障的举措。
发布方：中工网（工人日报）
发布日期：2026-05-09
适用范围：全国
原文链接：https://www.workercn.cn/c/2026-05-09/8797218.shtml', 0, 'PUBLISHED', 'OFFICIAL', 'OFFICIAL_MEDIA', '中工网（工人日报）', '中通', NULL, '2026-05-09', 'https://www.workercn.cn/c/2026-05-09/8797218.shtml'),
  ('十余万快递员权益有了实打实保障', '权益保障', '报道十余万快递员在派费、保险等方面的权益保障落地情况。
发布方：中工网（工人日报）
发布日期：2026-08-06
适用范围：全国
原文链接：https://www.workercn.cn/c/2026-08-06/8864049.shtml', 0, 'PUBLISHED', 'OFFICIAL', 'OFFICIAL_MEDIA', '中工网（工人日报）', NULL, NULL, '2026-08-06', 'https://www.workercn.cn/c/2026-08-06/8864049.shtml'),
  ('1.2亿元淡季补贴，为一线收派员收入“加成”', '福利补贴', '快递企业投入1.2亿元淡季补贴，为一线收派员收入提供兜底加成。
发布方：中工网（工人日报）
发布日期：2026-08-27
适用范围：全国
原文链接：https://www.workercn.cn/c/2026-08-27/8879105.shtml', 0, 'PUBLISHED', 'OFFICIAL', 'OFFICIAL_MEDIA', '中工网（工人日报）', '京东物流', NULL, '2026-08-27', 'https://www.workercn.cn/c/2026-08-27/8879105.shtml'),
  ('权益有保障 奔跑有荣光｜关注新就业群体', '权益保障', '经济日报报道新就业群体权益保障与职业荣光主题的调研观察。
发布方：经济日报
发布日期：2026-01-01
适用范围：全国
原文链接：http://www.jingjiribao.cn/static/detail.jsp?id=644280', 0, 'PUBLISHED', 'OFFICIAL', 'OFFICIAL_MEDIA', '经济日报', NULL, NULL, '2026-01-01', 'http://www.jingjiribao.cn/static/detail.jsp?id=644280'),
  ('平台发力促“提技”，为小哥打通职业上升通道——培训、竞赛、认证一条龙，新就业群体也有了成长阶梯', '技能培训', '平台与各方以培训、竞赛、认证打通新就业群体职业成长通道。
发布方：中国青年网（中国青年报）
发布日期：2026-03-27
适用范围：全国
原文链接：http://qnzs.youth.cn/tsxq/202603/t20260327_16577641.htm', 0, 'PUBLISHED', 'OFFICIAL', 'OFFICIAL_MEDIA', '中国青年网（中国青年报）', NULL, NULL, '2026-03-27', 'http://qnzs.youth.cn/tsxq/202603/t20260327_16577641.htm'),
  ('聚焦新就业形态劳动者｜培训、竞赛、认证一条龙，新就业群体也有了成长阶梯', '技能培训', '报道新就业群体技能培训、竞赛与技能等级认证衔接成长阶梯的做法。
发布方：中工网（工人日报）
发布日期：2026-03-27
适用范围：全国
原文链接：https://www.workercn.cn/c/2026-03-27/8766231.shtml', 0, 'PUBLISHED', 'OFFICIAL', 'OFFICIAL_MEDIA', '中工网（工人日报）', NULL, NULL, '2026-03-27', 'https://www.workercn.cn/c/2026-03-27/8766231.shtml'),
  ('全国各级工会开办4.8万个暑期托管班', '子女关爱', '全国各级工会开办4.8万个暑期托管班，缓解职工与新就业群体子女看护难题。
发布方：中国青年网（中国青年报）
发布日期：2026-08-21
适用范围：全国
原文链接：http://news.youth.cn/gn/202608/t20260821_16826323.htm', 0, 'PUBLISHED', 'OFFICIAL', 'OFFICIAL_MEDIA', '中国青年网（中国青年报）', NULL, NULL, '2026-08-21', 'http://news.youth.cn/gn/202608/t20260821_16826323.htm'),
  ('关爱新就业形态劳动者 自治区总工会出台十项措施', '权益保障', '内蒙古自治区总工会出台十项措施关爱新就业形态劳动者。
发布方：内蒙古自治区人民政府门户网站
发布日期：2026-03-04
适用范围：全国
原文链接：https://www.nmg.gov.cn/zwyw/jrgz/202603/t20260304_2868043.html', 0, 'PUBLISHED', 'OFFICIAL', 'AUTHORITATIVE', '内蒙古自治区人民政府门户网站', NULL, NULL, '2026-03-04', 'https://www.nmg.gov.cn/zwyw/jrgz/202603/t20260304_2868043.html'),
  ('自治区总工会出台十项措施关爱新就业形态劳动者 快递员权益保障迎利好', '权益保障', '邮政管理部门解读自治区总工会十项措施对快递员权益保障的利好。
发布方：内蒙古自治区邮政管理局（国家邮政局系统）
发布日期：2026-03-10
适用范围：全国
原文链接：https://nm.spb.gov.cn/nmgzzqyzglj/c100057/c100060/202603/6c2eeeb7dd904713bda74956b69856ee.shtml', 0, 'PUBLISHED', 'OFFICIAL', 'AUTHORITATIVE', '内蒙古自治区邮政管理局（国家邮政局系统）', NULL, NULL, '2026-03-10', 'https://nm.spb.gov.cn/nmgzzqyzglj/c100057/c100060/202603/6c2eeeb7dd904713bda74956b69856ee.shtml'),
  ('为灵活就业和新就业形态劳动者织密保障网', '社会保险', '报道灵活就业与新就业形态劳动者社会保障网络建设进展。
发布方：中工网（工人日报）
发布日期：2026-01-01
适用范围：全国
原文链接：https://www.workercn.cn/c/2026-01-01/8698266.shtml', 0, 'PUBLISHED', 'OFFICIAL', 'OFFICIAL_MEDIA', '中工网（工人日报）', NULL, NULL, '2026-01-01', 'https://www.workercn.cn/c/2026-01-01/8698266.shtml'),
  ('全国总工会：3年发展新就业形态劳动者会员1073.6万人', '权益保障', '全国总工会三年吸纳新就业形态劳动者会员超1073万人，组织覆盖显著提升。
发布方：央广网
发布日期：2026-02-05
适用范围：全国
原文链接：https://news.cnr.cn/kuaixun/20260205/t20260205_527517158.shtml', 0, 'PUBLISHED', 'OFFICIAL', 'OFFICIAL_MEDIA', '央广网', NULL, NULL, '2026-02-05', 'https://news.cnr.cn/kuaixun/20260205/t20260205_527517158.shtml');
