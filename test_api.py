"""
生信云平台 API 测试运行器
数据驱动测试，测试数据定义在 test_data.py
"""
import sys
import time
import requests
from datetime import datetime
from test_data import TestConfig, Endpoint, ENDPOINTS, TEST_ORDER, EXPECTED_RESPONSE_FIELDS


class Colors:
    GREEN = "\033[92m"
    RED = "\033[91m"
    YELLOW = "\033[93m"
    CYAN = "\033[96m"
    BOLD = "\033[1m"
    DIM = "\033[2m"
    RESET = "\033[0m"


class TestRunner:
    def __init__(self, config: TestConfig = None):
        self.config = config or TestConfig()
        self.session = requests.Session()
        self.access_token = None
        self.refresh_token = None
        self.created_ids = {}
        self.results = {"pass": 0, "fail": 0, "skip": 0}
        self.errors = []

    def _print_header(self):
        print(f"\n{Colors.BOLD}{'='*60}")
        print(f"  生信云平台 API 测试")
        print(f"{'='*60}{Colors.RESET}")
        print(f"  地址: {self.config.base_url}")
        print(f"  账号: {self.config.username}")
        print(f"  时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        print(f"{Colors.BOLD}{'='*60}{Colors.RESET}\n")

    def _resolve_path(self, path: str) -> str:
        """替换路径中的占位符"""
        path = path.replace("{project_id}", str(self.created_ids.get("project_id", "")))
        path = path.replace("{pipeline_id}", str(self.created_ids.get("pipeline_id", "")))
        return path

    def _resolve_body(self, body: dict) -> dict:
        """替换请求体中的占位符"""
        if not body:
            return None
        resolved = {}
        for k, v in body.items():
            if isinstance(v, str):
                v = v.replace("{username}", self.config.username)
                v = v.replace("{password}", self.config.password)
                v = v.replace("{refresh_token}", self.refresh_token or "")
                v = v.replace("{timestamp}", str(int(time.time())))
                v = v.replace("{project_id}", str(self.created_ids.get("project_id", "")))
                v = v.replace("{pipeline_id}", str(self.created_ids.get("pipeline_id", "")))
                if v == "None":
                    v = None
            elif v is None and k == "projectId":
                v = self.created_ids.get("project_id")
            resolved[k] = v
        return resolved

    def _make_request(self, endpoint: Endpoint, token: str = None) -> requests.Response:
        """发送HTTP请求"""
        url = self.config.base_url + self._resolve_path(endpoint.path)
        headers = {}
        if token:
            headers["Authorization"] = f"Bearer {token}"
        if endpoint.body:
            headers["Content-Type"] = "application/json"

        kwargs = {
            "method": endpoint.method,
            "url": url,
            "headers": headers,
            "timeout": self.config.timeout,
        }
        if endpoint.body:
            kwargs["json"] = self._resolve_body(endpoint.body)

        return self.session.request(**kwargs)

    def _run_test(self, name: str, endpoint: Endpoint) -> bool:
        """执行单个测试"""
        # 检查依赖
        if endpoint.depends_on:
            # 从depends_on提取资源类型 (e.g., "admin.projects.create" -> "project")
            parts = endpoint.depends_on.split(".")
            resource_type = parts[1] if len(parts) > 1 else parts[0]
            # 去掉复数s (projects -> project, pipelines -> pipeline)
            if resource_type.endswith("s"):
                resource_type = resource_type[:-1]
            dep_key = f"{resource_type}_id"
            if dep_key not in self.created_ids or self.created_ids[dep_key] is None:
                print(f"  {Colors.DIM}⊘ SKIP  {name} (依赖未满足){Colors.RESET}")
                self.results["skip"] += 1
                return True

        try:
            # 获取token
            token = None
            if endpoint.requires_auth:
                if self.access_token:
                    token = self.access_token
                else:
                    print(f"  {Colors.RED}✗ FAIL  {name} (无Token){Colors.RESET}")
                    self.results["fail"] += 1
                    self.errors.append(f"{name}: 无可用Token")
                    return False

            # 特殊处理：未授权测试使用无效token
            if name == "auth.unauthorized":
                token = "invalid-token-12345"

            resp = self._make_request(endpoint, token)

            # 检查HTTP状态码
            if resp.status_code == 401 and endpoint.expected_code == 401:
                print(f"  {Colors.GREEN}✓ PASS  {name}{Colors.RESET}")
                self.results["pass"] += 1
                return True

            # 解析响应
            body = resp.json() if resp.status_code == 200 else {}
            api_code = body.get("code", resp.status_code)

            # 检查结果
            success = api_code == endpoint.expected_code
            if success:
                # 提取Token
                if name == "auth.admin_login" or name == "auth.front_login":
                    result = body.get("result", {})
                    self.access_token = result.get("accessToken")
                    self.refresh_token = result.get("refreshToken")

                if name == "auth.refresh_token":
                    result = body.get("result", {})
                    self.access_token = result.get("accessToken")

                # 记录创建的资源ID
                if name == "admin.projects.create":
                    result = body.get("result", {})
                    self.created_ids["project_id"] = result.get("id")

                if name == "admin.pipelines.create":
                    result = body.get("result", {})
                    self.created_ids["pipeline_id"] = result.get("id")

                # 自定义验证
                if name in EXPECTED_RESPONSE_FIELDS:
                    validator = EXPECTED_RESPONSE_FIELDS[name]
                    if not validator(body):
                        success = False

            if success:
                print(f"  {Colors.GREEN}✓ PASS  {name}{Colors.RESET}")
                self.results["pass"] += 1
            else:
                msg = body.get("message", f"code={api_code}")
                print(f"  {Colors.RED}✗ FAIL  {name} ({msg}){Colors.RESET}")
                self.results["fail"] += 1
                self.errors.append(f"{name}: {msg}")

            return success

        except requests.RequestException as e:
            print(f"  {Colors.RED}✗ FAIL  {name} (网络错误: {e}){Colors.RESET}")
            self.results["fail"] += 1
            self.errors.append(f"{name}: {e}")
            return False

    def run(self):
        """运行所有测试"""
        self._print_header()

        for group in TEST_ORDER:
            category = group[0].split(".")[0]
            if category == "health":
                cat_label = "健康检查"
            elif category == "auth":
                cat_label = "认证"
            elif category == "public":
                cat_label = "公开接口"
            elif category == "admin":
                cat_label = "管理后台"
            else:
                cat_label = category

            print(f"{Colors.CYAN}[{cat_label}]{Colors.RESET}")

            for test_name in group:
                endpoint = ENDPOINTS.get(test_name)
                if endpoint:
                    self._run_test(test_name, endpoint)
                else:
                    print(f"  {Colors.YELLOW}⊘ SKIP  {test_name} (未定义){Colors.RESET}")
                    self.results["skip"] += 1

            print()

        self._print_summary()

    def _print_summary(self):
        """打印测试摘要"""
        total = sum(self.results.values())
        passed = self.results["pass"]
        failed = self.results["fail"]

        print(f"{Colors.BOLD}{'='*60}")
        print(f"  测试摘要")
        print(f"{'='*60}{Colors.RESET}")
        print(f"  总计: {total}")
        print(f"  {Colors.GREEN}通过: {passed}{Colors.RESET}")
        print(f"  {Colors.RED if failed else Colors.GREEN}失败: {failed}{Colors.RESET}")
        if self.results["skip"]:
            print(f"  {Colors.YELLOW}跳过: {self.results['skip']}{Colors.RESET}")
        print(f"{Colors.BOLD}{'='*60}{Colors.RESET}")

        if self.errors:
            print(f"\n{Colors.RED}失败详情:{Colors.RESET}")
            for err in self.errors:
                print(f"  - {err}")

        print()
        if failed == 0:
            print(f"{Colors.GREEN}{Colors.BOLD}🎉 所有测试通过！{Colors.RESET}")
        else:
            print(f"{Colors.RED}{Colors.BOLD}❌ 有 {failed} 个测试失败{Colors.RESET}")


def main():
    # 支持命令行参数
    config = TestConfig()
    args = sys.argv[1:]
    i = 0
    while i < len(args):
        if args[i] == "--base-url" and i + 1 < len(args):
            config.base_url = args[i + 1]
            i += 2
        elif args[i] == "--username" and i + 1 < len(args):
            config.username = args[i + 1]
            i += 2
        elif args[i] == "--password" and i + 1 < len(args):
            config.password = args[i + 1]
            i += 2
        else:
            i += 1

    runner = TestRunner(config)
    runner.run()
    sys.exit(0 if runner.results["fail"] == 0 else 1)


if __name__ == "__main__":
    main()
