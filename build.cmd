REM script to run the ANT script

REM "JAVA_HOME=D:\Java\jdk1.8.0_211"
SET "JAVA_HOME=D:\Java\jdk-11.0.7"
SET JAVA_CMD=%JAVA_HOME%\bin\java.exe
SET "JAVA_OPTS="
SET "ANT_HOME=D:\tools\apache-ant-1.10.8"
SET "CLASSPATH=%ANT_HOME%\lib\ant-launcher.jar"

SET ANTCMD=-f build.xml build -l build.log

"%JAVA_CMD%" %JAVA_OPTS% -cp %CLASSPATH% org.apache.tools.ant.launch.Launcher %ANTCMD%

exit
