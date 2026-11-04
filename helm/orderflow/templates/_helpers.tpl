{{/* Common labels. Call with (dict "name" <component> "root" $) */}}
{{- define "orderflow.labels" -}}
app: {{ .name }}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
app.kubernetes.io/part-of: orderflow
app.kubernetes.io/managed-by: {{ .root.Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .root.Chart.Name .root.Chart.Version }}
{{- end }}

{{- define "orderflow.secretName" -}}
{{- default "orderflow-secrets" .Values.secrets.existingSecret -}}
{{- end }}

{{/* Image reference for an application service */}}
{{- define "orderflow.image" -}}
{{- $reg := .root.Values.image.registry -}}
{{- if $reg }}{{ $reg }}/{{ end }}orderflow-{{ .name }}:{{ .root.Values.image.tag }}
{{- end }}
