import e2e.test.fixture.Greeting

@main def run(): Unit = assert(Greeting.message == "resolved from S3")
