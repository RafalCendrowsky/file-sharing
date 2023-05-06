# File Sharing Application

## Description

This is a project to get familiar with the basics of Play. It includes:

* authentication with HTTP basic auth
* uploading, downloading and listing files from AWS S3 with Akka streams
* sharing files to unauthorized users

## Running the app
To run the app, first build it locally with

```sbt universal:packageZipTarBall```

And then run with docker compose

```docker compose up```

For the application to work you have to provide an .env file with credentials for AWS as well as an application secret
