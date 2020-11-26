FROM alpine 
ENTRYPOINT [ "sleep", "3600" ]
RUN echo "You're using version 1 of the sample app" && \
    touch -v /Version-1.txt 
