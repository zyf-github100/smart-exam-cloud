from pathlib import Path
from PIL import Image, ImageDraw, ImageFont


ROOT = Path(r"D:\javacode\smart-exam-cloud")
OUT_DIR = ROOT / "docs" / "design"


def load_font(size: int, bold: bool = False):
    candidates = []
    if bold:
        candidates.extend(
            [
                r"C:\Windows\Fonts\msyhbd.ttc",
                r"C:\Windows\Fonts\simhei.ttf",
            ]
        )
    candidates.extend(
        [
            r"C:\Windows\Fonts\msyh.ttc",
            r"C:\Windows\Fonts\simsun.ttc",
            r"C:\Windows\Fonts\arial.ttf",
        ]
    )
    for path in candidates:
        if Path(path).exists():
            return ImageFont.truetype(path, size=size)
    return ImageFont.load_default()


TITLE_FONT = load_font(42, bold=True)
LABEL_FONT = load_font(28, bold=True)
TEXT_FONT = load_font(24)
SMALL_FONT = load_font(20)


def draw_center_text(draw, box, text, font, fill):
    left, top, right, bottom = box
    bbox = draw.multiline_textbbox((0, 0), text, font=font, spacing=4, align="center")
    width = bbox[2] - bbox[0]
    height = bbox[3] - bbox[1]
    x = left + (right - left - width) / 2
    y = top + (bottom - top - height) / 2
    draw.multiline_text((x, y), text, font=font, fill=fill, spacing=4, align="center")


def rounded_box(draw, box, fill, outline, radius=24, width=3):
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def draw_arrow(draw, start, end, fill, width=3, arrow_size=12):
    x1, y1 = start
    x2, y2 = end
    draw.line((x1, y1, x2, y2), fill=fill, width=width)
    dx = x2 - x1
    dy = y2 - y1
    length = (dx * dx + dy * dy) ** 0.5 or 1
    ux = dx / length
    uy = dy / length
    px = -uy
    py = ux
    p1 = (x2, y2)
    p2 = (x2 - ux * arrow_size + px * arrow_size * 0.55, y2 - uy * arrow_size + py * arrow_size * 0.55)
    p3 = (x2 - ux * arrow_size - px * arrow_size * 0.55, y2 - uy * arrow_size - py * arrow_size * 0.55)
    draw.polygon([p1, p2, p3], fill=fill)


def draw_actor(draw, x, y, label):
    head_r = 24
    head_center = (x, y - 40)
    draw.ellipse(
        (head_center[0] - head_r, head_center[1] - head_r, head_center[0] + head_r, head_center[1] + head_r),
        outline="#243447",
        width=4,
    )
    draw.line((x, y - 16, x, y + 48), fill="#243447", width=4)
    draw.line((x - 34, y + 6, x + 34, y + 6), fill="#243447", width=4)
    draw.line((x, y + 48, x - 28, y + 95), fill="#243447", width=4)
    draw.line((x, y + 48, x + 28, y + 95), fill="#243447", width=4)
    draw_center_text(draw, (x - 90, y + 110, x + 90, y + 152), label, LABEL_FONT, "#102030")


def draw_usecase():
    width, height = 1800, 1180
    img = Image.new("RGB", (width, height), "#ffffff")
    draw = ImageDraw.Draw(img)

    draw_center_text(draw, (0, 30, width, 95), "图2-1 智能考试云平台系统用例图", TITLE_FONT, "#1d2d44")

    system_box = (330, 130, 1660, 1080)
    rounded_box(draw, system_box, "#f7fbff", "#4a6fa5", radius=30, width=4)
    draw_center_text(draw, (760, 145, 1230, 200), "智能考试云平台", LABEL_FONT, "#274c77")

    student = (140, 300)
    teacher = (140, 620)
    admin = (140, 920)
    draw_actor(draw, *student, "学生")
    draw_actor(draw, *teacher, "教师")
    draw_actor(draw, *admin, "管理员")

    usecases = {
        "登录认证": (860, 220),
        "查看我的考试": (520, 360),
        "开始考试 / 查看试卷": (860, 360),
        "保存答案 / 提交试卷": (1240, 360),
        "查看成绩与解析": (520, 530),
        "创建题目 / 试卷": (860, 530),
        "创建并发布考试": (1240, 530),
        "人工评分": (520, 700),
        "查看报表 / 风险": (860, 700),
        "用户与角色管理": (1240, 700),
        "系统配置 / 审计日志": (860, 870),
    }

    ellipse_boxes = {}
    for text, (cx, cy) in usecases.items():
        box = (cx - 150, cy - 42, cx + 150, cy + 42)
        ellipse_boxes[text] = box
        draw.ellipse(box, fill="#ffffff", outline="#5d7ea8", width=3)
        draw_center_text(draw, box, text, TEXT_FONT, "#203040")

    def left_mid(box):
        return (box[0], (box[1] + box[3]) // 2)

    link_color = "#5c677d"
    student_links = ["登录认证", "查看我的考试", "开始考试 / 查看试卷", "保存答案 / 提交试卷", "查看成绩与解析"]
    teacher_links = ["登录认证", "创建题目 / 试卷", "创建并发布考试", "人工评分", "查看报表 / 风险"]
    admin_links = ["登录认证", "用户与角色管理", "系统配置 / 审计日志", "查看报表 / 风险"]

    for name in student_links:
        draw_arrow(draw, (210, student[1] + 8), left_mid(ellipse_boxes[name]), link_color, width=3)
    for name in teacher_links:
        draw_arrow(draw, (210, teacher[1] + 8), left_mid(ellipse_boxes[name]), link_color, width=3)
    for name in admin_links:
        draw_arrow(draw, (210, admin[1] + 8), left_mid(ellipse_boxes[name]), link_color, width=3)

    draw.text((360, 1035), "说明：学生、教师、管理员通过统一入口访问平台，实际接口能力由角色与权限码联合控制。", font=SMALL_FONT, fill="#52606d")

    img.save(OUT_DIR / "fig-usecase.png")


def draw_service(draw, box, title, subtitle):
    rounded_box(draw, box, "#ffffff", "#4a6fa5", radius=22, width=3)
    title_box = (box[0], box[1] + 8, box[2], box[1] + 54)
    draw_center_text(draw, title_box, title, LABEL_FONT, "#1d3557")
    draw_center_text(draw, (box[0] + 12, box[1] + 52, box[2] - 12, box[3] - 10), subtitle, SMALL_FONT, "#495057")


def draw_databox(draw, box, text, fill="#eef4ff", outline="#7a96c2"):
    rounded_box(draw, box, fill, outline, radius=18, width=3)
    draw_center_text(draw, box, text, SMALL_FONT, "#26415e")


def draw_architecture():
    width, height = 2000, 1280
    img = Image.new("RGB", (width, height), "#ffffff")
    draw = ImageDraw.Draw(img)

    draw_center_text(draw, (0, 28, width, 92), "图2-2 智能考试云平台系统架构图", TITLE_FONT, "#1d2d44")

    web_box = (150, 120, 430, 220)
    gateway_box = (820, 120, 1120, 220)
    nacos_box = (1550, 110, 1850, 220)
    redis_box = (1530, 940, 1860, 1040)
    mq_box = (820, 900, 1160, 1020)

    draw_service(draw, web_box, "smart-exam-web", "Vue 3 + Vite\n前端控制台")
    draw_service(draw, gateway_box, "gateway-service", "统一入口 / JWT 鉴权\n路由转发")
    draw_service(draw, nacos_box, "Nacos", "服务发现\n统一配置中心")
    draw_databox(draw, redis_box, "Redis\n缓存 / 幂等 / 防重")
    draw_databox(draw, mq_box, "RabbitMQ\nexam.submitted / score.published", fill="#fff4e6", outline="#d08c60")

    services = {
        "auth-service": ((80, 360, 390, 470), "登录认证\nJWT 签发"),
        "user-service": ((470, 360, 780, 470), "用户信息\n角色查询"),
        "question-service": ((860, 360, 1170, 470), "题库管理\n试卷管理"),
        "exam-service": ((1250, 360, 1560, 470), "考试发布\n会话作答"),
        "grading-service": ((280, 620, 590, 730), "自动判卷\n人工评分"),
        "analysis-service": ((840, 620, 1150, 730), "成绩快照\n统计报表"),
        "admin-service": ((1400, 620, 1710, 730), "用户治理\n权限与配置"),
    }

    dbs = {
        "user_db": (90, 830, 360, 910),
        "question_db": (900, 830, 1170, 910),
        "exam_db": (1280, 830, 1550, 910),
        "grading_db": (290, 1080, 560, 1160),
        "analysis_db": (850, 1080, 1120, 1160),
        "admin_db": (1410, 1080, 1680, 1160),
    }

    for name, (box, subtitle) in services.items():
        draw_service(draw, box, name, subtitle)

    for name, box in dbs.items():
        draw_databox(draw, box, name)

    arrow = "#5c677d"
    draw_arrow(draw, (430, 170), (820, 170), arrow, width=4)

    gateway_targets = [
        (235, 360),
        (625, 360),
        (1015, 360),
        (1405, 360),
        (435, 620),
        (995, 620),
        (1555, 620),
    ]
    for target in gateway_targets:
        draw_arrow(draw, (970, 220), target, "#8d99ae", width=3)

    for svc_box in [box for box, _ in services.values()] + [gateway_box]:
        center_top = ((svc_box[0] + svc_box[2]) // 2, svc_box[1])
        draw_arrow(draw, (1700, 220), center_top, "#adb5bd", width=2)

    draw_arrow(draw, (1405, 470), (990, 900), "#c56b37", width=4)
    draw_arrow(draw, (990, 1020), (435, 620), "#c56b37", width=4)
    draw_arrow(draw, (435, 730), (990, 900), "#c56b37", width=4)
    draw_arrow(draw, (990, 1020), (995, 620), "#c56b37", width=4)

    draw_arrow(draw, (235, 470), (225, 830), arrow, width=3)
    draw_arrow(draw, (625, 470), (225, 830), arrow, width=3)
    draw_arrow(draw, (1015, 470), (1035, 830), arrow, width=3)
    draw_arrow(draw, (1405, 470), (1415, 830), arrow, width=3)
    draw_arrow(draw, (435, 730), (425, 1080), arrow, width=3)
    draw_arrow(draw, (995, 730), (985, 1080), arrow, width=3)
    draw_arrow(draw, (1555, 730), (1545, 1080), arrow, width=3)
    draw_arrow(draw, (1555, 730), (225, 830), "#8795a1", width=2)

    shared_redis_targets = [
        ((235, 470), (1530, 970)),
        ((625, 470), (1530, 980)),
        ((1015, 470), (1530, 990)),
        ((1405, 470), (1530, 1000)),
        ((435, 730), (1530, 1010)),
        ((995, 730), (1530, 1020)),
        ((1555, 730), (1530, 1030)),
    ]
    for start, end in shared_redis_targets:
        draw_arrow(draw, start, end, "#90a4ae", width=2)

    draw.text((110, 1215), "说明：前端通过网关访问各业务服务，考试与判卷、判卷与分析之间通过 RabbitMQ 异步解耦；Redis 提供缓存与幂等控制，Nacos 负责配置与注册发现。", font=SMALL_FONT, fill="#52606d")

    img.save(OUT_DIR / "fig-architecture.png")


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    draw_usecase()
    draw_architecture()


if __name__ == "__main__":
    main()
