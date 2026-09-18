# PortLookup
# windows端口查找工具
完全由通义灵码创建编写实现

界面已中文化：窗口标题、按钮、表头、右键菜单、提示对话框均显示中文，
状态列（LISTENING/ESTABLISHED 等）显示为中文，并修复了 UDP 端口查询与
"查看程序"列表在进程名含空格时错位的问题。

## 功能说明
- 查询按**本地端口精确匹配**：输入 80 只返回本地端口为 80 的连接，
  不会再把 8080、远端端口 80 等无关记录带出来。
- 端口号需为 1 到 65535 的整数，非法输入会给出明确提示。
- 查询、查看程序、结束进程均在后台执行，界面不会卡死；命令超时上限 15 秒。
- 结束进程前会弹出确认框，并拒绝结束后系统关键进程 PID 0（System Idle Process）
  与 PID 4（System）。
- 命令失败（如权限不足）会显示具体错误信息，而不是留下空白表格。
- 底部状态栏显示查询结果条数或失败原因。

## 编码说明（重要）
源文件为 UTF-8 编码，而 javac 在中文 Windows 上默认按 GBK 读取源码，
因此编译时必须显式指定 `-encoding UTF-8`，否则会报"编码 GBK 的不可映射字符"。

## 构建
直接运行构建脚本（自动包含 `-encoding UTF-8`）：
```
build.bat
```
或手动执行：
```
del *.class
javac -encoding UTF-8 PortLookupUtil.java
jar cfm PortLookupUtil.jar MANIFEST.MF PortLookupUtil*.class
```

## 调试
```
del *.class && javac -encoding UTF-8 PortLookupUtil.java && java PortLookupUtil
```

## 运行
双击 Start PortLookupUtil.vbs 脚本也可以运行。
```
java -jar PortLookupUtil.jar
```
