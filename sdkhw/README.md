1、整体指引：
  1)使用build.sh编译后打包;
  2)如果编译成功会在bin路径下生成可执行二进制文件"ecs.jar"；
  3)使用如下格式调用并调试程序，例如：
	Linux环境：	root> sh startup.sh /xxx/TrainData.txt /xxx/input.txt /xxx/output.txt
	(说明：TrainData.txt是历史数据文件，input.txt是其他参数输入文件，output.txt是输出文件)
	ps 以上步骤用于本地调试代码
  4)将源代码与makelist.txt更新后打包压缩到同一个压缩包下，压缩包格式为tar.gz或zip，即可上传答案。
  上传答案的目录格式为：
  ecs.tar.gz(文件格式可以为tar.gz或者zip格式，文件名称可以自定义，但不要包含中文或空格)
	├─-- bin/	
        └── code/
        └── makelist.txt
	├── build.sh		不可修改，否则会影响服务器打包编译
	├── ecs_tar.sh
	└── readme.txt	

2、SDK目录结构：
SDK-java.zip
├─bin/				 										二进制文件路径
│		└── ecs.jar								            可执行的jar文件，由一键式打包脚本生成
│		└── startup.sh										Linux环境一键式执行脚本(本地测试脚本)
├─code/														源代码路径
│	└── ecs/
│		├── bin/										    编译后创建的class文件
│		└── src/										    java源代码路径
│				└─com/
│					├─filetool/
│					│	├─main/
│					│	│		Main.java main函数源文件，不可修改
│					│	└─util/
│					│			FileUtil.java				提供读写文件等功能的源文件，不可修改
│					│			LogUtil.java				提供日志记录功能的源文件，不可修改
│					└─elasticcloudservice/
│						└─predict/
│								Predict.java					你要写代码的源文件，需要修改
├── build.sh												Linux环境编译脚本，生成ecs.jar，不可修改
├── makelist.txt										    需要编译的java文件，供脚本调用
├── ecs_tar.sh								                打包脚本，生成ecs.tar.gz(可以不使用，直接在windows上修改源代码和makelist.txt压缩成zip格式文件)
└── readme.txt											    你正在看的文件 -_-" 这不用介绍了吧
	注意：
	1)如果增加了源文件需要修改makelist.txt文件；
	2)如果运行时提示startup.sh文件第13行错误，请按照你本地系统情况修改此行，此脚本只是用来方便调用，修改后不会影响在判题平台的判题。

3、SDK代码说明：
  我们已经提供了保姆式的服务，你只需要做：
  完成Predict.java文件中的XXX方法。
  SDK已经实现了读取文件、按要求格式写文件以及打印开始和结束时间的功能。为了便于调试，SDK将读入的信息全部在屏幕输出，可根据自身的需要酌情删除此打印信息。
  注意：读取文件功能是指，将图的信息文件和路径信息文件按行读取到内存，其在内存中的存储格式仍是字符串格式。因为这些信息以什么格式存储涉及到算法设计，这样做是为了不禁锢你的思路。
