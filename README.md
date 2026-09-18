# PortLookup
# windows端口查找工具
完全由通义灵码创建编写实现

界面已中文化：窗口标题、按钮、表头、右键菜单、提示对话框均显示中文，
状态列（LISTENING/ESTABLISHED 等）显示为中文，并修复了 UDP 端口查询与
"查看程序"列表在进程名含空格时错位的问题。

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
