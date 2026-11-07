-- MallAgent 演示商品初始化脚本（非 Flyway，手工执行，可重复运行）。
-- 使用固定 SKU 码做幂等锚点；已存在的商品不会重复创建，也不会重复初始化库存。
-- 不修改 V1–V16；生产环境不要执行本脚本。
SET NAMES utf8mb4;

DROP TEMPORARY TABLE IF EXISTS demo_product_seed;
CREATE TEMPORARY TABLE demo_product_seed
(
    sku_code      VARCHAR(64)    NOT NULL PRIMARY KEY,
    category_name VARCHAR(64)    NOT NULL,
    name          VARCHAR(128)   NOT NULL,
    subtitle      VARCHAR(255),
    description   TEXT,
    image_path    VARCHAR(512),
    price         DECIMAL(18, 2) NOT NULL,
    market_price  DECIMAL(18, 2),
    stock         INT            NOT NULL,
    sort          INT            NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO demo_product_seed
    (sku_code, category_name, name, subtitle, description, image_path, price, market_price, stock, sort)
VALUES
('DEMO-DIGI-001', '数码科技', '云感降噪蓝牙耳机', '40小时续航，通勤更安静', '40小时续航，通勤更安静。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/digital-01.svg', '299.00', '399.00', '80', '900'),
('DEMO-HOME-001', '家居生活', '手作陶瓷马克杯', '温润釉面，开启慢生活', '温润釉面，开启慢生活。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/home-01.svg', '49.00', '69.00', '90', '899'),
('DEMO-WEAR-001', '服饰鞋包', '轻量连帽防晒衣', 'UPF50+，夏日通勤防晒', 'UPF50+，夏日通勤防晒。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/fashion-01.svg', '169.00', '219.00', '75', '898'),
('DEMO-OUT-001', '运动户外', '轻量折叠露营椅', '一秒展开，享受户外时光', '一秒展开，享受户外时光。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/outdoor-01.svg', '149.00', '199.00', '55', '897'),
('DEMO-BEAUTY-001', '个护美妆', '氨基酸温和洁面乳', '绵密泡沫，洗后不紧绷', '绵密泡沫，洗后不紧绷。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/beauty-01.svg', '69.00', '89.00', '95', '896'),
('DEMO-OFFICE-001', '办公文具', '黄铜极简书签', '手工打磨，阅读好伴侣', '手工打磨，阅读好伴侣。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/office-01.svg', '29.00', '39.00', '120', '895'),
('DEMO-DIGI-002', '数码科技', '复古机械键盘', '紧凑布局，清脆手感', '紧凑布局，清脆手感。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/digital-02.svg', '459.00', '599.00', '45', '894'),
('DEMO-HOME-002', '家居生活', '雪松香薰蜡烛', '木质暖香，点亮夜晚', '木质暖香，点亮夜晚。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/home-02.svg', '79.00', '99.00', '70', '893'),
('DEMO-WEAR-002', '服饰鞋包', '纯棉基础圆领T恤', '柔软透气，四季百搭', '柔软透气，四季百搭。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/fashion-02.svg', '89.00', '119.00', '120', '892'),
('DEMO-OUT-002', '运动户外', '运动贴身跑步腰包', '防泼水收纳，跑步不晃动', '防泼水收纳，跑步不晃动。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/outdoor-02.svg', '59.00', '79.00', '90', '891'),
('DEMO-BEAUTY-002', '个护美妆', '神经酰胺修护面霜', '补水锁水，稳定肌肤状态', '补水锁水，稳定肌肤状态。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/beauty-02.svg', '129.00', '169.00', '70', '890'),
('DEMO-OFFICE-002', '办公文具', '桌面多格收纳笔筒', '分区整理，桌面更清爽', '分区整理，桌面更清爽。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/office-02.svg', '49.00', '69.00', '90', '889'),
('DEMO-DIGI-003', '数码科技', '磁吸无线充电器', '15W快充，桌面更整洁', '15W快充，桌面更整洁。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/digital-03.svg', '129.00', '169.00', '90', '888'),
('DEMO-HOME-003', '家居生活', '云朵记忆棉靠枕', '慢回弹支撑，久坐更舒适', '慢回弹支撑，久坐更舒适。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/home-03.svg', '129.00', '169.00', '60', '887'),
('DEMO-WEAR-003', '服饰鞋包', '复古帆布托特包', '大容量通勤，轻松装下日常', '大容量通勤，轻松装下日常。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/fashion-03.svg', '129.00', '169.00', '85', '886'),
('DEMO-OUT-003', '运动户外', '加厚防滑瑜伽垫', '高回弹支撑，居家运动', '高回弹支撑，居家运动。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/outdoor-03.svg', '119.00', '159.00', '60', '885'),
('DEMO-BEAUTY-003', '个护美妆', '雨后森林香水 50ml', '清透木质调，自然留香', '清透木质调，自然留香。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/beauty-03.svg', '199.00', '259.00', '50', '884'),
('DEMO-OFFICE-003', '办公文具', '软皮简约笔记本', '顺滑纸张，记录灵感', '顺滑纸张，记录灵感。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/office-03.svg', '39.00', '59.00', '100', '883'),
('DEMO-DIGI-004', '数码科技', '4K轻运动相机', '防抖拍摄，记录每一次出发', '防抖拍摄，记录每一次出发。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/digital-04.svg', '899.00', '1099.00', '35', '882'),
('DEMO-HOME-004', '家居生活', '可折叠布艺收纳箱', '大容量收纳，不占空间', '大容量收纳，不占空间。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/home-04.svg', '69.00', '89.00', '85', '881'),
('DEMO-WEAR-004', '服饰鞋包', '软底舒适乐福鞋', '轻便不累脚，通勤一整天', '轻便不累脚，通勤一整天。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/fashion-04.svg', '299.00', '399.00', '45', '880'),
('DEMO-OUT-004', '运动户外', '户外真空保温壶', '大容量保温，露营好搭档', '大容量保温，露营好搭档。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/outdoor-04.svg', '189.00', '239.00', '50', '879'),
('DEMO-BEAUTY-004', '个护美妆', '便携化妆刷五件套', '柔软刷毛，轻松上妆', '柔软刷毛，轻松上妆。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/beauty-04.svg', '79.00', '99.00', '65', '878'),
('DEMO-OFFICE-004', '办公文具', '按动中性笔六支装', '速干墨水，书写流畅', '速干墨水，书写流畅。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/office-04.svg', '19.00', '29.00', '160', '877'),
('DEMO-DIGI-005', '数码科技', '全天候智能手环', '心率睡眠监测，轻巧佩戴', '心率睡眠监测，轻巧佩戴。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/digital-05.svg', '239.00', '299.00', '75', '876'),
('DEMO-HOME-005', '家居生活', '棉麻格纹桌布', '自然肌理，为餐桌添温度', '自然肌理，为餐桌添温度。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/home-05.svg', '89.00', '119.00', '55', '875'),
('DEMO-WEAR-005', '服饰鞋包', '羊毛混纺围巾', '细腻亲肤，秋冬氛围感', '细腻亲肤，秋冬氛围感。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/fashion-05.svg', '159.00', '199.00', '60', '874'),
('DEMO-OUT-005', '运动户外', '速干冷感运动毛巾', '轻盈吸水，运动必备', '轻盈吸水，运动必备。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/outdoor-05.svg', '35.00', '49.00', '130', '873'),
('DEMO-BEAUTY-005', '个护美妆', '乳木果滋润护手霜', '绵润不黏腻，随时呵护双手', '绵润不黏腻，随时呵护双手。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/beauty-05.svg', '39.00', '49.00', '120', '872'),
('DEMO-OFFICE-005', '办公文具', '透明便携文件夹', '分类收纳，文件不折角', '分类收纳，文件不折角。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/office-05.svg', '15.00', '25.00', '150', '871'),
('DEMO-DIGI-006', '数码科技', '桌面环绕音箱', '小体积也有宽广声场', '小体积也有宽广声场。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/digital-06.svg', '199.00', '259.00', '60', '870'),
('DEMO-HOME-006', '家居生活', '自动晴雨两用伞', '轻盈抗风，晴雨随行', '轻盈抗风，晴雨随行。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/home-06.svg', '119.00', '149.00', '75', '869'),
('DEMO-WEAR-006', '服饰鞋包', '直筒休闲长裤', '垂感面料，修饰腿型', '垂感面料，修饰腿型。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/fashion-06.svg', '199.00', '249.00', '70', '868'),
('DEMO-OUT-006', '运动户外', '迷你筋膜放松枪', '多档调节，缓解运动疲劳', '多档调节，缓解运动疲劳。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/outdoor-06.svg', '249.00', '329.00', '40', '867'),
('DEMO-BEAUTY-006', '个护美妆', '清爽温和卸妆水', '轻松卸净，清爽不刺激', '轻松卸净，清爽不刺激。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/beauty-06.svg', '59.00', '79.00', '100', '866'),
('DEMO-OFFICE-006', '办公文具', '无线充电护眼台灯', '无频闪调光，桌面更整洁', '无频闪调光，桌面更整洁。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/office-06.svg', '199.00', '259.00', '45', '865'),
('DEMO-DIGI-007', '数码科技', '七合一USB-C扩展坞', '一线连接多种设备', '一线连接多种设备。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/digital-07.svg', '169.00', '219.00', '70', '864'),
('DEMO-HOME-007', '家居生活', '食品级硅胶厨具套装', '不伤锅具，烹饪更安心', '不伤锅具，烹饪更安心。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/home-07.svg', '99.00', '139.00', '50', '863'),
('DEMO-WEAR-007', '服饰鞋包', '极简防泼水双肩包', '分区收纳，城市轻出行', '分区收纳，城市轻出行。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/fashion-07.svg', '239.00', '299.00', '55', '862'),
('DEMO-OUT-007', '运动户外', '夜间反光运动臂带', '高亮反光，夜跑更安全', '高亮反光，夜跑更安全。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/outdoor-07.svg', '25.00', '39.00', '110', '861'),
('DEMO-BEAUTY-007', '个护美妆', '轻透防晒乳 SPF50', '清透成膜，日常防晒', '清透成膜，日常防晒。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/beauty-07.svg', '99.00', '129.00', '85', '860'),
('DEMO-OFFICE-007', '办公文具', '大屏静音计算器', '按键灵敏，办公更高效', '按键灵敏，办公更高效。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/office-07.svg', '69.00', '89.00', '70', '859'),
('DEMO-DIGI-008', '数码科技', '轻量化无线鼠标', '静音按键，长时间办公不累', '静音按键，长时间办公不累。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/digital-08.svg', '99.00', '139.00', '110', '858'),
('DEMO-HOME-008', '家居生活', '简约蘑菇床头灯', '三档柔光，伴你入眠', '三档柔光，伴你入眠。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/home-08.svg', '159.00', '209.00', '45', '857'),
('DEMO-WEAR-008', '服饰鞋包', '露指针织保暖手套', '灵活触屏，温暖不臃肿', '灵活触屏，温暖不臃肿。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/fashion-08.svg', '39.00', '59.00', '100', '856'),
('DEMO-OUT-008', '运动户外', '三节便携登山杖', '铝合金轻量，徒步更省力', '铝合金轻量，徒步更省力。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/outdoor-08.svg', '159.00', '199.00', '45', '855'),
('DEMO-BEAUTY-008', '个护美妆', '山茶花护发精油', '柔顺光泽，改善毛躁', '柔顺光泽，改善毛躁。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/beauty-08.svg', '69.00', '89.00', '80', '854'),
('DEMO-DIGI-009', '数码科技', '27英寸护眼显示器', '2K高清，低蓝光不闪屏', '2K高清，低蓝光不闪屏。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/digital-09.svg', '1099.00', '1399.00', '25', '853'),
('DEMO-HOME-009', '家居生活', '316不锈钢保温杯', '长效保温，随手带出门', '长效保温，随手带出门。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/home-09.svg', '139.00', '179.00', '80', '852'),
('DEMO-WEAR-009', '服饰鞋包', '透气运动袜三双装', '吸湿排汗，日常舒适', '吸湿排汗，日常舒适。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/fashion-09.svg', '29.00', '39.00', '150', '851'),
('DEMO-DIGI-010', '数码科技', '智能定时插座', '远程控制，家电更省心', '远程控制，家电更省心。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/digital-10.svg', '69.00', '99.00', '100', '850'),
('DEMO-HOME-010', '家居生活', '可撕式衣物粘毛器', '轻松去除浮毛，出门更体面', '轻松去除浮毛，出门更体面。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/home-10.svg', '19.00', '29.00', '140', '849'),
('DEMO-WEAR-010', '服饰鞋包', '百搭水洗棒球帽', '修饰脸型，轻松出门', '修饰脸型，轻松出门。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/fashion-10.svg', '69.00', '89.00', '90', '848'),
('DEMO-DIGI-011', '数码科技', '铝合金折叠支架', '稳固承托，随身携带', '稳固承托，随身携带。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/digital-11.svg', '49.00', '69.00', '120', '847'),
('DEMO-HOME-011', '家居生活', '浴室速干吸水地垫', '柔软防滑，保持地面干爽', '柔软防滑，保持地面干爽。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/home-11.svg', '59.00', '79.00', '70', '846'),
('DEMO-DIGI-012', '数码科技', '蓝牙防丢追踪器', '双向提醒，重要物品不再遗失', '双向提醒，重要物品不再遗失。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/digital-12.svg', '89.00', '119.00', '85', '845'),
('DEMO-HOME-012', '家居生活', '实木桌面收纳托盘', '钥匙杂物各有归处', '钥匙杂物各有归处。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/home-12.svg', '79.00', '99.00', '65', '844'),
('DEMO-DIGI-013', '数码科技', '便携固态硬盘 1TB', '高速传输，轻装出行', '高速传输，轻装出行。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/digital-13.svg', '599.00', '749.00', '40', '843'),
('DEMO-DIGI-014', '数码科技', '2K高清网络摄像头', '自动对焦，视频会议更清晰', '自动对焦，视频会议更清晰。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/digital-14.svg', '279.00', '349.00', '55', '842'),
('DEMO-DIGI-015', '数码科技', '电子温湿度计', '实时监测，守护居家舒适', '实时监测，守护居家舒适。MallAgent 精选材质与实用设计，适合日常使用，为生活增添一份从容与品质感。', '/demo-products/digital-15.svg', '59.00', '79.00', '100', '841');

INSERT INTO product_category (parent_id, name, level, sort, status, created_by, updated_by)
SELECT 0, seed.name, 1, seed.sort, 1, 1, 1
FROM (
    SELECT '数码科技' AS name, -100 AS sort
    UNION ALL SELECT '家居生活', -99
    UNION ALL SELECT '服饰鞋包', -98
    UNION ALL SELECT '运动户外', -97
    UNION ALL SELECT '个护美妆', -96
    UNION ALL SELECT '办公文具', -95
) AS seed
WHERE NOT EXISTS (
    SELECT 1 FROM product_category c
    WHERE c.parent_id = 0 AND c.name = seed.name AND c.deleted = 0
);

DROP PROCEDURE IF EXISTS mallagent_seed_demo_products;

DELIMITER //
CREATE PROCEDURE mallagent_seed_demo_products()
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE v_sku_code VARCHAR(64);
    DECLARE v_category_name VARCHAR(64);
    DECLARE v_name VARCHAR(128);
    DECLARE v_subtitle VARCHAR(255);
    DECLARE v_description TEXT;
    DECLARE v_image_path VARCHAR(512);
    DECLARE v_price DECIMAL(18, 2);
    DECLARE v_market_price DECIMAL(18, 2);
    DECLARE v_stock INT;
    DECLARE v_sort INT;
    DECLARE v_existing_sku BIGINT;
    DECLARE v_category_id BIGINT;
    DECLARE v_spu_id BIGINT;
    DECLARE v_sku_id BIGINT;
    DECLARE cur CURSOR FOR
        SELECT sku_code, category_name, name, subtitle, description, image_path,
               price, market_price, stock, sort
        FROM demo_product_seed ORDER BY sort DESC;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    OPEN cur;
    seed_loop: LOOP
        FETCH cur INTO v_sku_code, v_category_name, v_name, v_subtitle, v_description,
            v_image_path, v_price, v_market_price, v_stock, v_sort;
        IF done = 1 THEN
            LEAVE seed_loop;
        END IF;

        SET v_existing_sku = (
            SELECT id FROM product_sku
            WHERE sku_code = v_sku_code AND deleted = 0
            ORDER BY id DESC LIMIT 1
        );
        IF v_existing_sku IS NULL THEN
            SET v_category_id = (
                SELECT id FROM product_category
                WHERE parent_id = 0 AND name = v_category_name AND deleted = 0
                ORDER BY id ASC LIMIT 1
            );
            IF v_category_id IS NOT NULL THEN
                INSERT INTO product_spu
                    (category_id, brand_id, name, subtitle, main_image, description,
                     status, sort, created_by, updated_by)
                VALUES
                    (v_category_id, NULL, v_name, v_subtitle, v_image_path, v_description,
                     'ON_SHELF', v_sort, 1, 1);
                SET v_spu_id = LAST_INSERT_ID();

                INSERT INTO product_spu_image (spu_id, image_url, sort)
                VALUES (v_spu_id, v_image_path, 0);

                INSERT INTO product_sku
                    (spu_id, sku_code, price, market_price, image_url, status, spec_signature,
                     created_by, updated_by)
                VALUES
                    (v_spu_id, v_sku_code, v_price, v_market_price, v_image_path, 1, 'DEFAULT',
                     1, 1);
                SET v_sku_id = LAST_INSERT_ID();

                INSERT INTO stock_sku
                    (sku_id, available_stock, locked_stock, version, created_by, updated_by)
                VALUES (v_sku_id, v_stock, 0, 0, 1, 1);

                INSERT INTO stock_flow
                    (biz_type, biz_no, ref_biz_no, sku_id, quantity, change_available,
                     change_locked, available_before, available_after, locked_before,
                     locked_after, request_token, remark, created_by)
                VALUES
                    ('ADMIN_ADJUST', CONCAT('DEMO-STOCK-', v_sku_code), NULL, v_sku_id, v_stock,
                     v_stock, 0, 0, v_stock, 0, 0, UUID(), '演示商品初始库存', 1);
            END IF;
        END IF;
    END LOOP;
    CLOSE cur;
END//
DELIMITER ;

CALL mallagent_seed_demo_products();
DROP PROCEDURE IF EXISTS mallagent_seed_demo_products;
DROP TEMPORARY TABLE IF EXISTS demo_product_seed;

SELECT COUNT(*) AS demo_product_count
FROM product_sku
WHERE sku_code LIKE 'DEMO-%' AND deleted = 0;
