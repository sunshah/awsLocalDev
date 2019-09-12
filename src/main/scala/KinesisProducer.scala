import java.nio.ByteBuffer
import java.util.UUID

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.kinesis.AmazonKinesisClientBuilder
import com.amazonaws.services.kinesis.producer.{KinesisProducer, KinesisProducerConfiguration}
import com.amazonaws.{ClientConfiguration, Protocol}
import com.google.common.util.concurrent.{FutureCallback, Futures, ListenableFuture, MoreExecutors}

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.language.postfixOps
import scala.util.{Failure, Success}
import scala.util.control.Breaks._

object KinesisClientTest {

  def main(args: Array[String]): Unit = {

    val streamName = "iterable-ds-events-stream"
    val shardCount = 1
    val region = "us-east-1"
    val endpoint = "localhost"
    val port = 4568

    implicit val ec = ExecutionContext.global

    System.setProperty("com.amazonaws.sdk.disableCbor", "true")
    System.setProperty("com.amazonaws.sdk.disableCertChecking", "true")

    val clientConfiguration = new ClientConfiguration()
    clientConfiguration.setProtocol(Protocol.HTTP)
    val amazonKinesisClientBuilder = AmazonKinesisClientBuilder.standard()
    amazonKinesisClientBuilder.setCredentials(new DefaultAWSCredentialsProviderChain)
    amazonKinesisClientBuilder.setEndpointConfiguration(
      new EndpointConfiguration(s"https://$endpoint:$port", region)
    )
    amazonKinesisClientBuilder.setClientConfiguration(clientConfiguration)

    val amazonKinesisClient = amazonKinesisClientBuilder.build()
    val streams = amazonKinesisClient.listStreams()
    streams.getStreamNames.asScala.map(println(_))
    if (streams.getStreamNames.asScala.contains(streamName)) {
      println(s"Stream $streamName available")
    } else {
      amazonKinesisClient.createStream(streamName, shardCount)
      val startTime = System.currentTimeMillis()
      val maxWaitTime = startTime + 120 * 1000
      val sleepTime = 5000
      breakable {
        while (System.currentTimeMillis() < maxWaitTime) {
          val streamDesc = amazonKinesisClient.describeStream(streamName)
          if (streamDesc.getStreamDescription.getStreamStatus.equals("ACTIVE")) {
            println(s"Stream $streamName is active")
            break()
          } else {
            println("Stream not active, waiting..")
            Thread.sleep(sleepTime)
          }
        }
      }
    }

    val testMessage = "Hello this is a test of the kinesis stream publish and read hopefull this works"
    val messageText = testMessage.split(" ")
    val Separator = "@@@"

    val kinesisProducerConfiguration = new KinesisProducerConfiguration()
      .setRecordMaxBufferedTime(1000)
      .setRegion(region)
      .setAggregationEnabled(false)
      .setKinesisEndpoint(endpoint)
      .setKinesisPort(port)
      .setCloudwatchEndpoint(endpoint)
      .setRequestTimeout(1000)
      .setVerifyCertificate(false)

    // Single producer per service instance
    val kinesisProducer = new KinesisProducer(kinesisProducerConfiguration)

    case class Data(messageId: String, totalParts: Int, currentPart: Int, body: String) {
      def toByteBuffer() = {
        val bytes = messageId.getBytes ++ Separator.getBytes ++ totalParts.toString.getBytes() ++ Separator.getBytes ++ currentPart.toString.getBytes ++ Separator.getBytes ++ body.getBytes
        ByteBuffer.wrap(bytes)
      }

      def intToByteArray(int: Int): Array[Byte] = {
        BigInt(int).toByteArray
      }
    }

    val dataToPublish = messageText.zipWithIndex.map {
      case (body, index) =>
        Data(UUID.randomUUID().toString, messageText.length, index, body).toByteBuffer()
    }

    val addRecordResults = Future.sequence(
      dataToPublish.map { data =>
        val partitionBy = data.hashCode().toString
        kinesisProducer.addUserRecord(streamName, partitionBy, data).asScala()
      }.toList)


    addRecordResults onComplete {
      case Success(results) => {
        results.map { result =>
          if (result.isSuccessful) {
            println(s"record publish is success=${result.isSuccessful}, sequence=${result.getSequenceNumber}, shard-iterator = ${result.getShardId}")
            result.isSuccessful
          } else {
            println(s"failed to publish record. Exception = ${result.getSequenceNumber}")
          }
        }

      }
      case Failure(exception) =>
        println(s"Failed to publish result due to exception ${exception.getMessage}")
    }

    Await.ready(addRecordResults, 5 seconds)
  }

  implicit class FutureHelper[T](f: ListenableFuture[T]) {

    def asScala(): Future[T] = {
      val promise = Promise[T]
      Futures.addCallback(f, new FutureCallback[T] {
        def onSuccess(result: T) = promise.success(result)

        def onFailure(t: Throwable) = promise.failure(t)

      }, MoreExecutors.directExecutor())
      promise.future
    }
  }

}
