### Kinesis Producer-Consumer Local Dev and Testing

This app lets you test code to produce and consume from a kinesis stream by setting up local instances of kinesis, dynamoDB
 and CloudWatch using [localstack](https://github.com/localstack/localstack). It also sets up an nginx reverse proxy
 required to redirect outgoing http requests to aws. 

The `KinesisInputDStream` builder does not give you the option to point to a local instance of DynamoDB.
The Shard Reader is hard-coded to update aws's prod instance. To get around this problem we've setup a 
reverse proxy to capture outgoing calls to prod server and redirect them to a local DynamoDB instance. We do the same for
Cloudwatch

#### Kinesis Producer
The kinesis producer uses the KCL to create a new stream if it does not exist and publish messages to kinesis with the 
following message format
`<messageId>@@@<totalParts>@@@<currentPart>@@@<payload>`

#### Kinesis Consumer
The kinesis consumer sets up shard consumers using Spark structured streaming and prints records to the console 
 
### Local Development
Update hosts file to redirect outgoing aws requests to loopback interface on which localstack is setup
* Add the following entry to your `/etc/hosts` file
```
127.0.0.1 dynamodb.us-east-1.amazonaws.com
127.0.0.1 monitoring.us-east-1.amazonaws.com
```

Start up localstack and nginx containers
* `docker-compose up -d`

Both the producer and consumer use the [Default Credential Provider Chain](https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/credentials.html)
Ensure one of the credentials is provided. This is required by the Kinesis client library and Kinesis producer library.
Localstack does not perform any authentication

## Appendix

### Debugging commands

#### Kinesis 
* `aws --region= --endpoint-url=https://localhost:4568 kinesis list-streams --no-verify-ssl`
* `aws --region= --endpoint-url=https://localhost:4568 kinesis describe-stream --stream-name iterable-ds-events-stream --no-verify-ssl`
* `aws --region= --endpoint-url=https://localhost:4568 kinesis get-shard-iterator --shard-id <shardId-> --shard-iterator-type TRIM_HORIZON --stream-name iterable-ds-events-stream --query 'ShardIterator' --no-verify-ssl`
* `aws --region= --endpoint-url=https://localhost:4568 kinesis get-records --shard-iterator <iterator_from_previous_command>  --no-verify-ssl`

#### DynamoDB
* `aws dynamodb list-tables --endpoint-url=https://localhost:4569 --no-verify-ssl --debug`

### Generating self signed certs

`openssl req -subj '/CN=localhost' -x509 -newkey rsa:4096 -nodes -keyout key.pem -out cert.pem -days 365`

### Redirecting outbound http request
modify `/etc/hosts`

Add entry `127.0.0.0 <outgoing_url>` to redirect traffic destined to aws dynamoDB and Cloudwatch

### Challenges
* Cannot configure the dynamoDB url, therefore need to setup reverse proxy using nginx and redirecting
traffic destined to to local gateway `127.0.0.1` [Kinesis Stream Issue](https://github.com/localstack/localstack/issues/677)

### Reading
* https://www.thepolyglotdeveloper.com/2017/03/nginx-reverse-proxy-containerized-docker-applications/
* https://dev.to/domysee/setting-up-a-reverse-proxy-with-nginx-and-docker-compose-29jg 