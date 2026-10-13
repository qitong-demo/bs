-- 政策与资讯种子数据（由 scripts/generate-news-seed.mjs 生成，请勿手工编辑）
--
-- 数据来源：data/policy-sources.json，每条都带可打开的原文链接。
-- city_code 为 NULL 表示不限城市（国家层面或全国性平台政策），在所有城市都可见；
-- 非空时只在该城市可见，并在该城市排到最前面。
-- source_type 区分官方（政府/群团/官方媒体）与平台企业自行发布。

USE friendly_station;

DELETE FROM news;

INSERT INTO news (title, category, content, view_count, status, source_type, publisher, platform, city_code, published_at, source_url) VALUES
  ('中共中央办公厅 国务院办公厅关于加强新就业群体服务管理的意见', '权益保障', '中办国办首个新就业群体服务管理专项意见，部署党建、权益、算法治理与友好场景建设。
发布方：中共中央办公厅、国务院办公厅
发布日期：2025-10-29
适用范围：全国
原文链接：https://www.court.gov.cn/xinshidai/xiangqing/498441.html', 0, 'PUBLISHED', 'OFFICIAL', '中共中央办公厅、国务院办公厅', NULL, NULL, '2025-10-29', 'https://www.court.gov.cn/xinshidai/xiangqing/498441.html'),
  ('关于维护新就业形态劳动者劳动保障权益的指导意见（人社部发〔2021〕56号）', '权益保障', '新就业形态权益保障的纲领性文件，确立劳动关系三分法与平台算法、申诉等责任。
发布方：人力资源社会保障部等八部门
发布日期：2021-07-16
适用范围：全国
原文链接：https://www.gov.cn/zhengce/zhengceku/2021-07/23/content_5626761.htm', 0, 'PUBLISHED', 'OFFICIAL', '人力资源社会保障部等八部门', NULL, NULL, '2021-07-16', 'https://www.gov.cn/zhengce/zhengceku/2021-07/23/content_5626761.htm'),
  ('关于扩大新就业形态人员职业伤害保障试点的通知（人社部发〔2025〕24号）', '职业伤害', '三年分步扩围职业伤害保障试点，新增十省份与滴滴、顺丰同城、满帮等平台。
发布方：人力资源社会保障部等九部门
发布日期：2025-04-22
适用范围：全国
原文链接：https://www.gov.cn/gongbao/2025/issue_12186/202507/content_7034087.html', 0, 'PUBLISHED', 'OFFICIAL', '人力资源社会保障部等九部门', NULL, NULL, '2025-04-22', 'https://www.gov.cn/gongbao/2025/issue_12186/202507/content_7034087.html'),
  ('《新就业形态劳动者权益保障办法》公开征求意见', '权益保障', '新就业形态权益保障拟由指导意见升格为部门规章，公开向社会征求意见。
发布方：司法部智慧普法平台（转载人力资源社会保障部征求意见稿）
发布日期：2026-10-09
适用范围：全国
原文链接：http://legalinfo.moj.gov.cn/zhfxfzzx/fzzxyw/202610/t20261009_540436.html', 0, 'PUBLISHED', 'OFFICIAL', '司法部智慧普法平台（转载人力资源社会保障部征求意见稿）', NULL, NULL, '2026-10-09', 'http://legalinfo.moj.gov.cn/zhfxfzzx/fzzxyw/202610/t20261009_540436.html'),
  ('七部门联合印发关于做好快递员群体合法权益保障工作的意见', '权益保障', '七部门明确快递员派费、社保、休息与投诉救济等权益保障要求的专项意见。
发布方：国家邮政局等七部门
发布日期：2021-07-01
适用范围：全国
原文链接：https://www.spb.gov.cn/gjyzj/c100015/c100016/202107/8f863298776f4797a8e24bedcd7f47a7.shtml', 0, 'PUBLISHED', 'OFFICIAL', '国家邮政局等七部门', NULL, NULL, '2021-07-01', 'https://www.spb.gov.cn/gjyzj/c100015/c100016/202107/8f863298776f4797a8e24bedcd7f47a7.shtml'),
  ('制定派费指引、创新参保方式 让快递员权益更有保障', '劳动报酬', '国家邮政局解读快递员派费指引与优先参保等配套举措，稳定末端收入。
发布方：国家邮政局
发布日期：2021-07-01
适用范围：全国
原文链接：https://www.spb.gov.cn/gjyzj/c200058/202107/4d52492973cb4ed88c5e8f9dbace01d8.shtml', 0, 'PUBLISHED', 'OFFICIAL', '国家邮政局', NULL, NULL, '2021-07-01', 'https://www.spb.gov.cn/gjyzj/c200058/202107/4d52492973cb4ed88c5e8f9dbace01d8.shtml'),
  ('新就业群体服务管理工作部署推进会召开', '驿站服务', '中央社会工作部部署推进新就业群体服务管理，明确阵地建设与诉求响应任务。
发布方：中共中央社会工作部
发布日期：2026-04-16
适用范围：全国
原文链接：http://www.zyshgzb.gov.cn/n1/2026/0416/c462103-40702301.html', 0, 'PUBLISHED', 'OFFICIAL', '中共中央社会工作部', NULL, NULL, '2026-04-16', 'http://www.zyshgzb.gov.cn/n1/2026/0416/c462103-40702301.html'),
  ('给新就业群体更多暖心服务', '驿站服务', '中央社会工作部阐释以暖心服务凝聚新就业群体的工作思路与阵地要求。
发布方：中共中央社会工作部
发布日期：2025-11-27
适用范围：全国
原文链接：https://www.zyshgzb.gov.cn/n1/2025/1127/c461360-40612759.html', 0, 'PUBLISHED', 'OFFICIAL', '中共中央社会工作部', NULL, NULL, '2025-11-27', 'https://www.zyshgzb.gov.cn/n1/2025/1127/c461360-40612759.html'),
  ('不止歇脚，更有安心', '驿站服务', '介绍新就业群体服务阵地从歇脚功能向权益咨询、诉求受理等综合服务升级。
发布方：中共中央社会工作部
发布日期：2026-08-21
适用范围：全国
原文链接：https://www.zyshgzb.gov.cn/n1/2026/0821/c462551-40783844.html', 0, 'PUBLISHED', 'OFFICIAL', '中共中央社会工作部', NULL, NULL, '2026-08-21', 'https://www.zyshgzb.gov.cn/n1/2026/0821/c462551-40783844.html'),
  ('以心暖“新” 连“新”共治（凝“新”聚力）', '权益保障', '报道地方以关爱服务引导新就业群体参与基层治理与共治的做法。
发布方：中共中央社会工作部
发布日期：2026-06-25
适用范围：全国
原文链接：https://www.zyshgzb.gov.cn/n1/2026/0625/c462300-40747278.html', 0, 'PUBLISHED', 'OFFICIAL', '中共中央社会工作部', NULL, NULL, '2026-06-25', 'https://www.zyshgzb.gov.cn/n1/2026/0625/c462300-40747278.html'),
  ('党建引领新就业群体融入城市治理（学思践悟）', '权益保障', '理论文章总结以党建带动新就业群体融入城市治理的路径与机制。
发布方：中共中央社会工作部
发布日期：2026-02-27
适用范围：全国
原文链接：https://www.zyshgzb.gov.cn/n1/2026/0227/c461796-40671251.html', 0, 'PUBLISHED', 'OFFICIAL', '中共中央社会工作部', NULL, NULL, '2026-02-27', 'https://www.zyshgzb.gov.cn/n1/2026/0227/c461796-40671251.html'),
  ('街巷奔跑者，城市守护者', '权益保障', '报道新就业群体参与城市治理并获得关爱服务的典型故事。
发布方：中共中央社会工作部
发布日期：2026-10-13
适用范围：全国
原文链接：https://www.zyshgzb.gov.cn/n1/2026/1013/c462733-40813531.html', 0, 'PUBLISHED', 'OFFICIAL', '中共中央社会工作部', NULL, NULL, '2026-10-13', 'https://www.zyshgzb.gov.cn/n1/2026/1013/c462733-40813531.html'),
  ('我国将建立健全职业伤害保障制度', '职业伤害', '人社部门明确职业伤害保障制度将从试点走向全面建立。
发布方：中国就业网（人力资源社会保障部主管）
发布日期：2025-12-01
适用范围：全国
原文链接：https://chinajob.mohrss.gov.cn/h5/c/2025-12-01/466090.shtml', 0, 'PUBLISHED', 'OFFICIAL', '中国就业网（人力资源社会保障部主管）', NULL, NULL, '2025-12-01', 'https://chinajob.mohrss.gov.cn/h5/c/2025-12-01/466090.shtml'),
  ('十八万个工会驿站汇聚暖流（青年圆桌·关注新就业群体）', '驿站服务', '全国工会驿站已达18万个，成为户外劳动者与新就业群体基础服务网络。
发布方：人民日报
发布日期：2025-02-06
适用范围：全国
原文链接：http://paper.people.com.cn/rmrb/pad/content/202502/06/content_30055037.html', 0, 'PUBLISHED', 'OFFICIAL', '人民日报', NULL, NULL, '2025-02-06', 'http://paper.people.com.cn/rmrb/pad/content/202502/06/content_30055037.html'),
  ('打出组合拳为网约车司机“撑腰”', '权益保障', '交通运输部门推出网约车司机权益保障组合措施，含抽成公开与申诉渠道。
发布方：新华网
发布日期：2022-05-18
适用范围：全国
原文链接：https://www.news.cn/politics/2022-05/18/c_1128659761.htm', 0, 'PUBLISHED', 'OFFICIAL', '新华网', NULL, NULL, '2022-05-18', 'https://www.news.cn/politics/2022-05/18/c_1128659761.htm'),
  ('上海市人力资源和社会保障局等九部门关于扩大新就业形态人员职业伤害保障试点工作的通知', '职业伤害', '上海落实国家扩围要求，明确本市职业伤害保障试点的参保与经办安排。
发布方：上海市人力资源和社会保障局等九部门
发布日期：2025-07-29
适用城市：上海市
原文链接：https://www.shanghai.gov.cn/rcjysj/20250924/dfcf444ca49548488ea2441894cdbfd6.html', 0, 'PUBLISHED', 'OFFICIAL', '上海市人力资源和社会保障局等九部门', NULL, '310100', '2025-07-29', 'https://www.shanghai.gov.cn/rcjysj/20250924/dfcf444ca49548488ea2441894cdbfd6.html'),
  ('深圳市人民代表大会常务委员会关于加强新就业形态劳动者权益保障的决定', '权益保障', '深圳以人大决定形式立法保障新就业形态劳动者，涵盖报酬、算法与休息权。
发布方：深圳市人民代表大会常务委员会
发布日期：2025-11-17
适用城市：深圳市
原文链接：https://www.sz.gov.cn/zfgb/2025/gb1393/content/post_12505038.html', 0, 'PUBLISHED', 'OFFICIAL', '深圳市人民代表大会常务委员会', NULL, '440300', '2025-11-17', 'https://www.sz.gov.cn/zfgb/2025/gb1393/content/post_12505038.html'),
  ('深圳出台新就业形态劳动者权益保障决定 为新就业群体构建法治化保障体系', '权益保障', '官方解读深圳人大决定的主要内容与法治化保障思路。
发布方：深圳市人民政府门户网站
发布日期：2025-11-17
适用城市：深圳市
原文链接：https://www.sz.gov.cn/ztfw/jylyfwzt/wyk_184808/content/mpost_12495987.html', 0, 'PUBLISHED', 'OFFICIAL', '深圳市人民政府门户网站', NULL, '440300', '2025-11-17', 'https://www.sz.gov.cn/ztfw/jylyfwzt/wyk_184808/content/mpost_12495987.html'),
  ('市人大常委会出台加强新就业形态劳动者权益保障的决定 系列细致规定营造友好和谐劳动生态', '算法与规则', '报道深圳决定中关于算法考核、报酬规则等细化规定。
发布方：深圳市人民政府门户网站
发布日期：2025-10-31
适用城市：深圳市
原文链接：https://www.sz.gov.cn/cn/xxgk/zfxxgj/zwdt/content/mpost_12467472.html', 0, 'PUBLISHED', 'OFFICIAL', '深圳市人民政府门户网站', NULL, '440300', '2025-10-31', 'https://www.sz.gov.cn/cn/xxgk/zfxxgj/zwdt/content/mpost_12467472.html'),
  ('考核指标不得损害劳动者安全健康', '算法与规则', '全国人大网站报道地方立法明确平台考核指标不得危害劳动者安全健康。
发布方：中国人大网
发布日期：2025-11-25
适用范围：全国
原文链接：http://www.npc.gov.cn/c2/c30834/202511/t20251125_449675.html', 0, 'PUBLISHED', 'OFFICIAL', '中国人大网', NULL, NULL, '2025-11-25', 'http://www.npc.gov.cn/c2/c30834/202511/t20251125_449675.html'),
  ('深圳为170万新就业形态劳动者构筑制度保障', '权益保障', '广东省政府网报道深圳为约170万新就业形态劳动者建立制度保障体系。
发布方：广东省人民政府门户网站
发布日期：2026-02-26
适用城市：深圳市
原文链接：http://www.gd.gov.cn/gdywdt/dsdt/content/post_4860071.html', 0, 'PUBLISHED', 'OFFICIAL', '广东省人民政府门户网站', NULL, '440300', '2026-02-26', 'http://www.gd.gov.cn/gdywdt/dsdt/content/post_4860071.html'),
  ('关于印发杭州市维护新就业形态劳动者劳动保障权益实施办法（试行）的通知', '权益保障', '杭州出台新就业形态劳动者权益保障实施办法，细化用工与报酬规则。
发布方：杭州市人民政府
发布日期：2022-05-12
适用城市：杭州市
原文链接：http://www.hangzhou.gov.cn/art/2022/5/12/art_1229063383_1815758.html', 0, 'PUBLISHED', 'OFFICIAL', '杭州市人民政府', NULL, '330100', '2022-05-12', 'http://www.hangzhou.gov.cn/art/2022/5/12/art_1229063383_1815758.html'),
  ('“爱心驿家”为新就业群体筑起温馨家', '驿站服务', '杭州以“爱心驿家”为品牌建设新就业群体服务阵地并提供关爱服务。
发布方：杭州市人民政府
发布日期：2026-01-26
适用城市：杭州市
原文链接：https://www.hangzhou.gov.cn/col/col812270/art/2026/art_3cc85d4e0de14d0bb700c125b944e80a.html', 0, 'PUBLISHED', 'OFFICIAL', '杭州市人民政府', NULL, '330100', '2026-01-26', 'https://www.hangzhou.gov.cn/col/col812270/art/2026/art_3cc85d4e0de14d0bb700c125b944e80a.html'),
  ('上城打造新就业群体友好城区样板', '驿站服务', '杭州上城区建设新就业群体友好城区，形成阵地服务与诉求响应样板。
发布方：浙江省人民政府门户网站
发布日期：2025-11-06
适用城市：杭州市
原文链接：https://www.zj.gov.cn/col/col1554469/art/2025/art_f680634f3d12f865cadfb954b36cda2e.html', 0, 'PUBLISHED', 'OFFICIAL', '浙江省人民政府门户网站', NULL, '330100', '2025-11-06', 'https://www.zj.gov.cn/col/col1554469/art/2025/art_f680634f3d12f865cadfb954b36cda2e.html'),
  ('西湖区共建共享全域友好社会', '驿站服务', '杭州西湖区推进全域友好社会建设，落地面向新就业群体的友好场景。
发布方：浙江省人民政府门户网站
发布日期：2026-01-29
适用城市：杭州市
原文链接：https://www.zj.gov.cn/col/col1554469/art/2026/art_b88577a04e5c4ef970969c619ec42671.html', 0, 'PUBLISHED', 'OFFICIAL', '浙江省人民政府门户网站', NULL, '330100', '2026-01-29', 'https://www.zj.gov.cn/col/col1554469/art/2026/art_b88577a04e5c4ef970969c619ec42671.html'),
  ('用心用情打造“小哥之家”——浙江省杭州市全方位关爱新就业群体', '驿站服务', '杭州建设“小哥之家”等服务阵地，为骑手提供歇脚、充电等关爱服务。
发布方：人民网
发布日期：2024-11-14
适用城市：杭州市
原文链接：http://society.people.com.cn/n1/2024/1114/c1008-40360601.html', 0, 'PUBLISHED', 'OFFICIAL', '人民网', NULL, '330100', '2024-11-14', 'http://society.people.com.cn/n1/2024/1114/c1008-40360601.html'),
  ('杭州市人力资源和社会保障局关于市十四届人大六次会议拱墅23号建议的答复', '社会保险', '杭州人社部门答复人大建议，说明新就业形态劳动者参保等保障进展。
发布方：杭州市人力资源和社会保障局
发布日期：2026-08-04
适用城市：杭州市
原文链接：https://hrss.hangzhou.gov.cn/col/col1229578408/art/2026/art_768afdf263eea3995ff3f7e444e7ae09.html', 0, 'PUBLISHED', 'OFFICIAL', '杭州市人力资源和社会保障局', NULL, '330100', '2026-08-04', 'https://hrss.hangzhou.gov.cn/col/col1229578408/art/2026/art_768afdf263eea3995ff3f7e444e7ae09.html'),
  ('北京新就业群体服务升级', '驿站服务', '北京推动新就业群体服务升级，拓展驿站与关爱服务供给。
发布方：人民网北京频道
发布日期：2025-03-22
适用城市：北京市
原文链接：http://bj.people.com.cn/n2/2025/0322/c14540-41171993.html', 0, 'PUBLISHED', 'OFFICIAL', '人民网北京频道', NULL, '110100', '2025-03-22', 'http://bj.people.com.cn/n2/2025/0322/c14540-41171993.html'),
  ('友好之城·温暖同行 为首都友好之城故事注入双井“新”声', '驿站服务', '北京朝阳双井街道推进新就业群体友好场景建设与关爱服务。
发布方：北京市朝阳区人民政府
发布日期：2026-01-26
适用城市：北京市
原文链接：http://www.bjchy.gov.cn/dynamic/jxdt/4028805a9bded2a3019bf99843be14fd.html', 0, 'PUBLISHED', 'OFFICIAL', '北京市朝阳区人民政府', NULL, '110100', '2026-01-26', 'http://www.bjchy.gov.cn/dynamic/jxdt/4028805a9bded2a3019bf99843be14fd.html'),
  ('广州荔湾：实施“凝‘新’聚荔”党旗红惠民工程 织密新就业群体“幸福网”', '驿站服务', '广州荔湾以党建惠民工程织密新就业群体服务网络与关爱举措。
发布方：中共广东省委组织部
发布日期：2025-12-04
适用城市：广州市
原文链接：https://www.gdzz.gov.cn/zzjs/jcdjc/content/post_24316.html', 0, 'PUBLISHED', 'OFFICIAL', '中共广东省委组织部', NULL, '440100', '2025-12-04', 'https://www.gdzz.gov.cn/zzjs/jcdjc/content/post_24316.html'),
  ('广州：“小个专”，出“骑”效', '权益保障', '市场监管总局介绍广州以党建带动骑手群体服务与管理的做法。
发布方：国家市场监督管理总局（小微企业个体工商户专业市场党建）
发布日期：2025-11-06
适用城市：广州市
原文链接：https://xgz.samr.gov.cn/gzjl/zdjs/art/2025/art_4764aaed52894c758b0a46d857dc3130.html', 0, 'PUBLISHED', 'OFFICIAL', '国家市场监督管理总局（小微企业个体工商户专业市场党建）', NULL, '440100', '2025-11-06', 'https://xgz.samr.gov.cn/gzjl/zdjs/art/2025/art_4764aaed52894c758b0a46d857dc3130.html'),
  ('把“新”声放心里，变诉求为福祉', '权益保障', '深圳龙华建立新就业群体诉求收集与办理机制，把诉求转化为具体福祉。
发布方：深圳市龙华区人民政府
发布日期：2026-07-27
适用城市：深圳市
原文链接：https://www.szlhq.gov.cn/bmxxgk/qtgzb/dtxx_124446/gzdt_124447/content/post_12910312.html', 0, 'PUBLISHED', 'OFFICIAL', '深圳市龙华区人民政府', NULL, '440300', '2026-07-27', 'https://www.szlhq.gov.cn/bmxxgk/qtgzb/dtxx_124446/gzdt_124447/content/post_12910312.html'),
  ('湖北武汉：武昌区工会驿站跑出工伤救助“加速度”', '职业伤害', '武汉武昌区工会驿站协助新就业群体快速办理工伤救助。
发布方：中国就业网（人力资源社会保障部主管）
发布日期：2026-04-24
适用城市：武汉市
原文链接：https://chinajob.mohrss.gov.cn/c/2026-04-24/532050.shtml', 0, 'PUBLISHED', 'OFFICIAL', '中国就业网（人力资源社会保障部主管）', NULL, '420100', '2026-04-24', 'https://chinajob.mohrss.gov.cn/c/2026-04-24/532050.shtml');
