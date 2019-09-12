name := "aws"

version := "0.1"

scalaVersion := "2.12.0"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk-kinesis" % "1.11.615",
  "com.amazonaws" % "amazon-kinesis-producer" % "0.12.11",
  "org.apache.spark" %% "spark-core" % "2.4.0",
  "org.apache.spark" %% "spark-sql" % "2.4.0",
  "org.apache.spark" %% "spark-streaming-kinesis-asl" % "2.4.0",
  "org.apache.spark" %% "spark-streaming" % "2.4.0"
)