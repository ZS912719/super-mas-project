FROM grafana/grafana:13.1.2@sha256:d177053ab62253815f130d81504f77063baf5fd4ca93299d6048453bd31e047a
USER root
RUN mkdir -p /var/lib/grafana/data && chown 472:0 /var/lib/grafana/data
USER 472
RUN grafana cli --pluginsDir /var/lib/grafana/plugins plugins install yesoreyeram-infinity-datasource 4.1.6
