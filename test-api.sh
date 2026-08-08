#!/bin/bash
# 生信云平台 API 全面测试脚本
# 用法: bash test-api.sh

BASE_URL="http://localhost:8080"
PASS=0
FAIL=0
ERRORS=""

# 颜色
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

# 测试函数
test_api() {
    local method=$1
    local url=$2
    local desc=$3
    local data=$4
    local token=$5
    
    local http_code
    local body
    
    if [ "$method" = "POST" ] && [ -n "$data" ]; then
        if [ -n "$token" ]; then
            body=$(curl -s -X POST -H "Content-Type: application/json" -H "Authorization: Bearer $token" -d "$data" "$url" 2>/dev/null)
        else
            body=$(curl -s -X POST -H "Content-Type: application/json" -d "$data" "$url" 2>/dev/null)
        fi
    elif [ "$method" = "PUT" ] && [ -n "$data" ]; then
        if [ -n "$token" ]; then
            body=$(curl -s -X PUT -H "Content-Type: application/json" -H "Authorization: Bearer $token" -d "$data" "$url" 2>/dev/null)
        else
            body=$(curl -s -X PUT -H "Content-Type: application/json" -d "$data" "$url" 2>/dev/null)
        fi
    elif [ "$method" = "DELETE" ]; then
        if [ -n "$token" ]; then
            body=$(curl -s -X DELETE -H "Authorization: Bearer $token" "$url" 2>/dev/null)
        else
            body=$(curl -s -X DELETE "$url" 2>/dev/null)
        fi
    else
        if [ -n "$token" ]; then
            body=$(curl -s -X $method -H "Authorization: Bearer $token" "$url" 2>/dev/null)
        else
            body=$(curl -s -X $method "$url" 2>/dev/null)
        fi
    fi
    
    # 获取HTTP状态码
    if [ -n "$token" ]; then
        http_code=$(curl -s -o /dev/null -w "%{http_code}" -X $method -H "Authorization: Bearer $token" "$url" 2>/dev/null)
    else
        http_code=$(curl -s -o /dev/null -w "%{http_code}" -X $method "$url" 2>/dev/null)
    fi
    
    local code=$(echo "$body" | grep -o '"code":[0-9]*' | head -1 | cut -d: -f2)
    
    if [ "$http_code" = "200" ] && [ "$code" = "200" ]; then
        echo -e "${GREEN}✓${NC} $desc (HTTP:${http_code}, code:${code})"
        PASS=$((PASS+1))
    else
        echo -e "${RED}✗${NC} $desc (HTTP:${http_code:-N/A}, code:${code:-N/A})"
        if [ -n "$body" ]; then
            local msg=$(echo "$body" | grep -o '"message":"[^"]*"' | head -1)
            if [ -n "$msg" ]; then
                echo -e "    ${YELLOW}→ $msg${NC}"
            fi
        fi
        FAIL=$((FAIL+1))
        ERRORS="$ERRORS\n  $desc: HTTP=$result code=$code"
    fi
}

echo "=========================================="
echo "  生信云平台 API 全面测试"
echo "=========================================="
echo ""

# ===== 1. 基础连通性 =====
echo -e "${YELLOW}[1] 基础连通性${NC}"
test_api GET "$BASE_URL/api/front/pipelines/list" "公开API:流程列表"
echo ""

# ===== 2. 登录认证 =====
echo -e "${YELLOW}[2] 登录认证${NC}"
test_api POST "$BASE_URL/api/front/auth/login" "公开登录" '{"username":"admin","password":"admin123"}'

# 获取admin token
ADMIN_TOKEN=$(curl -s -X POST "$BASE_URL/api/admin/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}' 2>/dev/null | \
    grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)

if [ -n "$ADMIN_TOKEN" ]; then
    echo -e "${GREEN}✓${NC} Admin Token获取成功 (${#ADMIN_TOKEN}字符)"
    PASS=$((PASS+1))
else
    echo -e "${RED}✗${NC} Admin Token获取失败"
    FAIL=$((FAIL+1))
    echo -e "\n${RED}无法继续测试，Token获取失败${NC}"
    exit 1
fi

test_api POST "$BASE_URL/api/admin/auth/login" "Admin登录" '{"username":"admin","password":"admin123"}'
test_api GET "$BASE_URL/api/admin/auth/userInfo" "获取用户信息" "" "$ADMIN_TOKEN"
echo ""

# ===== 3. 公开接口 (无需Token) =====
echo -e "${YELLOW}[3] 公开接口${NC}"
test_api GET "$BASE_URL/api/front/projects/list" "公开项目列表"
test_api GET "$BASE_URL/api/front/pipelines/list" "公开流程列表"
test_api GET "$BASE_URL/api/front/pipelines/categories" "流程分类列表"
test_api GET "$BASE_URL/api/front/agent/tools" "Agent工具列表"
echo ""

# ===== 4. 项目管理 =====
echo -e "${YELLOW}[4] 项目管理${NC}"
test_api GET "$BASE_URL/api/admin/projects/list?pageNum=1&pageSize=10" "项目列表" "" "$ADMIN_TOKEN"
test_api POST "$BASE_URL/api/admin/projects/create" "创建项目" '{"name":"测试项目","description":"自动化测试创建","ownerId":1}' "$ADMIN_TOKEN"
# 获取项目ID
PROJECT_ID=$(curl -s "$BASE_URL/api/admin/projects/list?pageNum=1&pageSize=1" -H "Authorization: Bearer $ADMIN_TOKEN" 2>/dev/null | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
if [ -n "$PROJECT_ID" ]; then
    test_api GET "$BASE_URL/api/admin/projects/$PROJECT_ID" "获取项目详情" "" "$ADMIN_TOKEN"
    test_api PUT "$BASE_URL/api/admin/projects/update" "更新项目" "{\"id\":$PROJECT_ID,\"name\":\"测试项目-已更新\"}" "$ADMIN_TOKEN"
else
    echo -e "  ${YELLOW}⊘ 跳过项目详情/更新（无项目ID）${NC}"
fi
echo ""

# ===== 5. 流程管理 =====
echo -e "${YELLOW}[5] 流程管理${NC}"
test_api GET "$BASE_URL/api/admin/pipelines/list?pageNum=1&pageSize=10" "流程列表" "" "$ADMIN_TOKEN"
test_api POST "$BASE_URL/api/admin/pipelines/create" "创建流程" '{"name":"测试流程","description":"BWA比对流程","projectId":null}' "$ADMIN_TOKEN"
PIPELINE_ID=$(curl -s "$BASE_URL/api/admin/pipelines/list?pageNum=1&pageSize=1" -H "Authorization: Bearer $ADMIN_TOKEN" 2>/dev/null | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
if [ -n "$PIPELINE_ID" ]; then
    test_api GET "$BASE_URL/api/admin/pipelines/$PIPELINE_ID" "获取流程详情" "" "$ADMIN_TOKEN"
fi
echo ""

# ===== 6. 执行监控 =====
echo -e "${YELLOW}[6] 执行监控${NC}"
test_api GET "$BASE_URL/api/admin/executions/list?pageNum=1&pageSize=10" "执行列表" "" "$ADMIN_TOKEN"
echo ""

# ===== 7. 数据管理 =====
echo -e "${YELLOW}[7] 数据管理${NC}"
test_api GET "$BASE_URL/api/admin/datafiles/list?projectId=1&pageNum=1&pageSize=10" "数据文件列表" "" "$ADMIN_TOKEN"
echo ""

# ===== 8. 用户管理 =====
echo -e "${YELLOW}[8] 用户管理${NC}"
test_api GET "$BASE_URL/api/admin/users/list?pageNum=1&pageSize=10" "用户列表" "" "$ADMIN_TOKEN"
echo ""

# ===== 9. 角色管理 =====
echo -e "${YELLOW}[9] 角色管理${NC}"
test_api GET "$BASE_URL/api/admin/roles/list" "角色列表" "" "$ADMIN_TOKEN"
echo ""

# ===== 10. 系统管理 =====
echo -e "${YELLOW}[10] 系统管理${NC}"
test_api GET "$BASE_URL/api/admin/system/configs" "系统配置" "" "$ADMIN_TOKEN"
test_api GET "$BASE_URL/api/admin/system/dashboard" "Dashboard统计" "" "$ADMIN_TOKEN"
echo ""

# ===== 11. Agent智能体 =====
echo -e "${YELLOW}[11] Agent智能体${NC}"
test_api GET "$BASE_URL/api/admin/agent/tools" "Agent工具列表" "" "$ADMIN_TOKEN"
test_api GET "$BASE_URL/api/admin/agent/conversations" "对话列表" "" "$ADMIN_TOKEN"
echo ""

# ===== 12. 操作日志 =====
echo -e "${YELLOW}[12] 操作日志${NC}"
test_api GET "$BASE_URL/api/admin/logs/list?pageNum=1&pageSize=10" "操作日志" "" "$ADMIN_TOKEN"
echo ""

# ===== 清理测试数据 =====
echo -e "${YELLOW}[清理] 删除测试数据${NC}"
if [ -n "$PIPELINE_ID" ]; then
    test_api DELETE "$BASE_URL/api/admin/pipelines/$PIPELINE_ID" "删除测试流程" "" "$ADMIN_TOKEN"
fi
if [ -n "$PROJECT_ID" ]; then
    test_api DELETE "$BASE_URL/api/admin/projects/$PROJECT_ID" "删除测试项目" "" "$ADMIN_TOKEN"
fi
echo ""

# ===== 结果汇总 =====
echo "========================================="
echo -e "测试完成: ${GREEN}通过 $PASS${NC} / ${RED}失败 $FAIL${NC} / 总计 $((PASS+FAIL))"
echo "========================================="

if [ $FAIL -gt 0 ]; then
    echo -e "\n${RED}失败的接口需要修复${NC}"
    exit 1
else
    echo -e "\n${GREEN}🎉 所有接口测试通过！${NC}"
    exit 0
fi
