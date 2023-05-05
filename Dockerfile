# Use the official Java 11 image as the base image
FROM openjdk:11

# Set the working directory to /app
WORKDIR /app

# Copy the application jar file and configuration files into the container
COPY target/universal/file-sharing-1.0-SNAPSHOT.tgz /app
COPY conf /app/conf

# Extract the application tarball
RUN tar -xzf file-sharing-1.0-SNAPSHOT.tgz && rm file-sharing-1.0-SNAPSHOT.tgz

# Start the application
CMD ["./file-sharing-1.0-SNAPSHOT/bin/file-sharing"]