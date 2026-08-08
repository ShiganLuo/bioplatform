"""
生信云平台 API 测试数据配置
测试数据与测试逻辑解耦，便于维护和扩展
"""
from dataclasses import dataclass, field
from typing import Optional


@dataclass
class TestConfig:
    """测试配置"""
    base_url: str = "http://localhost:8080"
    username: str = "admin"
    password: str = "admin123"
    timeout: int = 10


@dataclass
class Endpoint:
    """API端点定义"""
    method: str
    path: str
    description: str
    body: Optional[dict] = None
    requires_auth: bool = True
    expected_code: int = 200
    depends_on: Optional[str] = None  # 依赖的资源ID


# ============ 端点注册表 ============

ENDPOINTS = {
    # 健康检查
    "health": Endpoint(
        method="GET",
        path="/api/front/pipelines/list",
        description="服务可达性检查",
        requires_auth=False,
    ),

    # 认证
    "auth.admin_login": Endpoint(
        method="POST",
        path="/api/admin/auth/login",
        description="管理员登录",
        body={"username": "{username}", "password": "{password}"},
        requires_auth=False,
    ),
    "auth.user_info": Endpoint(
        method="GET",
        path="/api/admin/auth/userInfo",
        description="获取当前用户信息",
    ),
    "auth.refresh_token": Endpoint(
        method="POST",
        path="/api/admin/auth/refreshToken",
        description="刷新访问令牌",
        body={"refreshToken": "{refresh_token}"},
    ),
    "auth.front_login": Endpoint(
        method="POST",
        path="/api/front/auth/login",
        description="前台用户登录",
        body={"username": "{username}", "password": "{password}"},
        requires_auth=False,
    ),
    "auth.unauthorized": Endpoint(
        method="GET",
        path="/api/admin/auth/userInfo",
        description="未授权访问应被拒绝",
        expected_code=403,  # Spring Security returns 403 for invalid tokens
    ),

    # 公开接口
    "public.projects": Endpoint(
        method="GET",
        path="/api/front/projects/list",
        description="公开项目列表",
        requires_auth=False,
    ),
    "public.pipelines": Endpoint(
        method="GET",
        path="/api/front/pipelines/list",
        description="公开流程列表",
        requires_auth=False,
    ),
    "public.categories": Endpoint(
        method="GET",
        path="/api/front/pipelines/categories",
        description="流程分类列表",
        requires_auth=False,
    ),
    "public.tools": Endpoint(
        method="GET",
        path="/api/front/agent/tools",
        description="Agent工具列表",
        requires_auth=False,
    ),

    # 管理后台 - 项目
    "admin.projects.list": Endpoint(
        method="GET",
        path="/api/admin/projects/list",
        description="项目列表",
    ),
    "admin.projects.create": Endpoint(
        method="POST",
        path="/api/admin/projects/create",
        description="创建项目",
        body={"name": "测试项目-{timestamp}", "description": "自动化测试", "ownerId": 1},
    ),
    "admin.projects.get": Endpoint(
        method="GET",
        path="/api/admin/projects/{project_id}",
        description="获取项目详情",
        depends_on="admin.projects.create",
    ),
    "admin.projects.update": Endpoint(
        method="PUT",
        path="/api/admin/projects/update",
        description="更新项目",
        body={"id": "{project_id}", "name": "测试项目-已更新", "description": "已更新", "status": 1},
        depends_on="admin.projects.create",
    ),
    "admin.projects.delete": Endpoint(
        method="DELETE",
        path="/api/admin/projects/{project_id}",
        description="删除项目",
        depends_on="admin.projects.create",
    ),

    # 管理后台 - 流程
    "admin.pipelines.list": Endpoint(
        method="GET",
        path="/api/admin/pipelines/list",
        description="流程列表",
    ),
    "admin.pipelines.create": Endpoint(
        method="POST",
        path="/api/admin/pipelines/create",
        description="创建流程",
        body={"name": "测试流程-{timestamp}", "description": "BWA比对", "projectId": None},
    ),
    "admin.pipelines.get": Endpoint(
        method="GET",
        path="/api/admin/pipelines/{pipeline_id}",
        description="获取流程详情",
        depends_on="admin.pipelines.create",
    ),
    "admin.pipelines.delete": Endpoint(
        method="DELETE",
        path="/api/admin/pipelines/{pipeline_id}",
        description="删除流程",
        depends_on="admin.pipelines.create",
    ),

    # 管理后台 - 其他
    "admin.executions.list": Endpoint(
        method="GET",
        path="/api/admin/executions/list",
        description="执行列表",
    ),
    "admin.datafiles.list": Endpoint(
        method="GET",
        path="/api/admin/datafiles/list?projectId=1",
        description="数据文件列表",
    ),
    "admin.users.list": Endpoint(
        method="GET",
        path="/api/admin/users/list",
        description="用户列表",
    ),
    "admin.roles.list": Endpoint(
        method="GET",
        path="/api/admin/roles/list",
        description="角色列表",
    ),
    "admin.system.configs": Endpoint(
        method="GET",
        path="/api/admin/system/configs",
        description="系统配置",
    ),
    "admin.system.dashboard": Endpoint(
        method="GET",
        path="/api/admin/system/dashboard",
        description="Dashboard统计",
    ),
    "admin.agent.tools": Endpoint(
        method="GET",
        path="/api/admin/agent/tools",
        description="Agent工具列表",
    ),
    "admin.agent.conversations": Endpoint(
        method="GET",
        path="/api/admin/agent/conversations",
        description="对话列表",
    ),
    "admin.logs.list": Endpoint(
        method="GET",
        path="/api/admin/logs/list",
        description="操作日志",
    ),
}


# ============ 测试执行顺序 ============

TEST_ORDER = [
    # 1. 健康检查
    ["health"],

    # 2. 认证
    [
        "auth.admin_login",
        "auth.user_info",
        "auth.refresh_token",
        "auth.front_login",
        "auth.unauthorized",
    ],

    # 3. 公开接口
    [
        "public.projects",
        "public.pipelines",
        "public.categories",
        "public.tools",
    ],

    # 4. 管理后台CRUD
    [
        "admin.projects.list",
        "admin.projects.create",
        "admin.projects.get",
        "admin.projects.update",
        "admin.pipelines.list",
        "admin.pipelines.create",
        "admin.pipelines.get",
        "admin.executions.list",
        "admin.datafiles.list",
        "admin.users.list",
        "admin.roles.list",
        "admin.system.configs",
        "admin.system.dashboard",
        "admin.agent.tools",
        "admin.agent.conversations",
        "admin.logs.list",
    ],

    # 5. 清理（逆序删除）
    [
        "admin.pipelines.delete",
        "admin.projects.delete",
    ],
]


# ============ 期望值定义 ============

EXPECTED_RESPONSE_FIELDS = {
    "auth.admin_login": lambda r: r.get("result", {}).get("accessToken"),
    "auth.user_info": lambda r: r.get("result", {}).get("username") == "admin",
    "auth.front_login": lambda r: r.get("result", {}).get("accessToken"),
    "admin.system.dashboard": lambda r: isinstance(r.get("result"), dict),
}
