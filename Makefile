# FlashMall 本地依赖环境快捷命令
# 目的：把常用 docker compose 命令标准化，减少手敲和记忆成本。

.PHONY: env-up env-down env-logs env-ps env-reset

# 启动本地 MySQL / Redis / RabbitMQ（后台模式）
env-up:
	docker compose up -d

# 停止并删除容器（保留数据卷）
env-down:
	docker compose down

# 持续查看日志（默认显示最近 150 行）
env-logs:
	docker compose logs -f --tail=150

# 查看容器状态（含健康检查）
env-ps:
	docker compose ps

# 强制重置环境：停止 + 删除卷（会清空数据库/MQ/Redis数据）
env-reset:
	docker compose down -v
