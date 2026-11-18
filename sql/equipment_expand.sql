-- 设备名录扩充：新增 10 台设备（15-24），保持与原有 14 台相同的字段规范
USE lab_equipment;
SET NAMES utf8mb4;

INSERT INTO equipment (name, model, category, location, status, description) VALUES
('凝胶成像系统', 'ChemiDoc', '光谱分析', '实验楼 C202', 'AVAILABLE', '化学发光与荧光凝胶成像，支持 Western Blot 与核酸胶拍摄'),
('磁力搅拌器', 'RCT Basic', '常规仪器', '实验楼 A105', 'AVAILABLE', '加热磁力搅拌，转速 50-1500 rpm，最高加热 340℃'),
('恒温摇床', 'THZ-82A', '常规仪器', '实验楼 B203', 'AVAILABLE', '台式恒温振荡器，控温范围 室温+5℃ ~ 60℃，转速 40-300 rpm'),
('恒温水浴锅', 'HH-4', '常规仪器', '实验楼 A106', 'AVAILABLE', '四孔数显恒温水浴，控温范围 室温+5℃ ~ 100℃，精度 ±0.5℃'),
('超声波清洗器', 'KQ-250DE', '清洗灭菌', '实验楼 A107', 'AVAILABLE', '双频数控超声清洗，功率 250 W，槽容 10 L'),
('液氮罐', 'YDS-30', '储存设备', '实验楼 C102', 'AVAILABLE', '30 L 液氮生物容器，静态蒸发率低，用于细胞与样本冻存'),
('细胞培养箱', '3111', '常规仪器', '实验楼 C204', 'AVAILABLE', 'CO₂ 直热式培养箱，控温 37℃，CO₂ 浓度 0-20%'),
('旋转蒸发仪', 'RE-52AA', '分离纯化', '实验楼 D202', 'AVAILABLE', '20-180 rpm 旋转蒸发，配循环水真空泵，适用于溶剂回收'),
('真空泵', 'SHB-III', '分离纯化', '实验楼 D203', 'AVAILABLE', '循环水式多用真空泵，极限真空度 0.098 MPa'),
('制冰机', 'ZB-20', '常规仪器', '实验楼 B202', 'AVAILABLE', '实验室雪花制冰机，日产冰量 20 kg，储冰 6 kg');

SELECT id, name, model, category, location, status FROM equipment ORDER BY id;
