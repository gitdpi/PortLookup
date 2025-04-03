# PortLookup
# windows端口查找工具
完全由通义灵码创建编写实现

调试命令
```
cmd
del *.class && javac PortLookupUtil.java && java PortLookupUtil
```

打包命令
```
del *.class
javac PortLookupUtil.java
jar cfm PortLookupUtil.jar MANIFEST.MF PortLookupUtil*.class
```
双击 Start PortLookupUtil.vbs 脚本也可以运行

运行命令
```
java -jar PortLookupUtil.jar
```
