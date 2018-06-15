VERSION = $(shell xmllint --xpath "//*[local-name()='project']/*[local-name()='version']/text()" pom.xml)
CWD = $(shell pwd)
IMAGE ?= nexus

help:
	@echo 'Usage:'
	@echo '  make <target>'
	@echo
	@echo 'Targets:'
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-30s\033[0m %s\n", $$1, $$2}'
	@echo
	@echo 'Examples:'
	@echo '  a) Build Nexus Plugin Locally:'
	@echo '     make build'
	@echo
	@echo '  b) Build and Create Docker Image "my/nexus":'
	@echo '     IMAGE=my/nexus make package'
	@echo

version:
	@echo $(VERSION)

build:		## Build Nexus Plugin Locally
	@docker run --rm -it -v $(CWD):$(CWD) -w $(CWD) maven:3.5.2 mvn clean package

package:	## Build and Create Docker Image (default: nexus)
	@docker build --no-cache --build-arg VERSION=$(VERSION) -t $(IMAGE) .
