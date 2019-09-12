import org.apache.log4j.{Level, Logger}
import org.apache.spark.SparkConf
import org.apache.spark.internal.Logging
import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.kinesis.KinesisInitialPositions.Latest
import org.apache.spark.streaming.kinesis.KinesisInputDStream
import org.apache.spark.streaming.{Milliseconds, StreamingContext}

object StructuredStreaming extends Serializable {

  def main(args: Array[String]): Unit = {

    // disable cert checking and Cbor
    System.setProperty("com.amazonaws.sdk.disableCbor", "true")
    System.setProperty("com.amazonaws.sdk.disableCertChecking", "true")

    val appName = "kinesis-consumer"
    val streamName = "iterable-ds-events-stream"
    val shardCount = 1
    val endpoint = "localhost"
    val port = 4568
    val batchInterval = Milliseconds(2000)
    val sparkConfig = new SparkConf().setAppName(appName).setMaster("local[2]")
    val ssc = new StreamingContext(sparkConfig, batchInterval)

    // change log level if needed
    //    ssc.sparkContext.setLogLevel("INFO")

    // get concurrent kinesis streams
    val kinesisInputDStream = (0 until shardCount).map { currentShard =>
      KinesisInputDStream.builder
        .streamingContext(ssc)
        .streamName(streamName)
        .endpointUrl(s"https://${endpoint}:${port}")
        .initialPosition(new Latest)
        .checkpointAppName(appName)
        .checkpointInterval(batchInterval)
        .storageLevel(StorageLevel.MEMORY_AND_DISK_2)
        .build()
    }

    // create a unified stream from the different shards
    val unionStreams = ssc.union(kinesisInputDStream)

    // unionStreams is a DStream, a sequence of RDDs representing a continuous stream of data
    val eventsDecode = unionStreams.flatMap { bytes =>
      new String(bytes).split(" ")
    }

    // split further by separator
    //    val eventsSplit = eventsDecode.flatMap { message =>
    //      message.split("@@@")
    //    }

    eventsDecode.print(100)

    ssc.start()
    ssc.awaitTermination()
  }

  object StreamLogging extends Logging with Serializable {
    @transient lazy val logger = Logger.getRootLogger

    def setLoggingLevel(level: Level): Unit = {
      val log4jInitialized = Logger.getRootLogger.getAllAppenders.hasMoreElements
      if (!log4jInitialized) {
        logInfo(s"Setting log level to ${level}")
        Logger.getRootLogger.setLevel(level)
      }
    }
  }

}
