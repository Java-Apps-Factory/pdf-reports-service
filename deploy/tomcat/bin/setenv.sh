#!/bin/sh
# Tomcat 9 JVM settings for the FIPS Report Service (SPEC section 16).
# Copy to $CATALINA_BASE/bin/setenv.sh on every node. Values in ${...:-default} form can be overridden in the
# environment of the Tomcat service.

# Must equal the DB2 server's time zone: timestamps (DATA_AS_OF, leases) are read through the JVM time zone.
REPORT_TZ="${REPORT_TZ:-America/New_York}"
# Temp directory for PDF generation; reserve at least 3 x largest PDF x generation workers (about 250 MB).
REPORT_TMP="${REPORT_TMP:-/data/tmp}"
REPORT_LOG_DIR="${REPORT_LOG_DIR:-/var/log/tomcat}"

CATALINA_OPTS="$CATALINA_OPTS -Xms2g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 \
  -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=$REPORT_LOG_DIR \
  -Xlog:gc*:file=$REPORT_LOG_DIR/gc.log:time,uptime:filecount=5,filesize=20m \
  -Djava.io.tmpdir=$REPORT_TMP -Duser.timezone=$REPORT_TZ \
  -Dspring.profiles.active=db2,sib,prod"
export CATALINA_OPTS
