[![Build Status](https://travis-ci.org/larscheid-schmitzhermes/nexus3-github-oauth-plugin.svg?branch=master)](https://travis-ci.org/larscheid-schmitzhermes/nexus3-github-oauth-plugin)
# Nexus3 Github OAuth Plugin
This plugin adds a Github realm to Sonatype Nexus OSS and enables you to authenticate with Github Users and authorize with Github Orgs and Teams.

The plugin does not implement a full OAuth flow, instead you use your github user name + an OAuth token you generated in your account to log in to the nexus. 
This works through the web as well as through tools like maven, gradle...

## Installation

#### 0. Prerequisites
* JDK 8 is installed
* Apache Maven is installed
* Sonatype Nexus OSS 3.2.x is installed 

##### Directory naming convention:
When Nexus gets downloaded and unzipped, there are typically two directories created:
* nexus-3.2.1-01
* sonatype-work/nexus3

To avoid confusion, the conventions of the Sonatype reference will be used in the following descriptions:
* nexus-3.2.1-01 will be referred to as **$install-dir**
* sonatype-work/nexus3 will be referred to as **$data-dir**

See [https://books.sonatype.com/nexus-book/reference3/install.html#directories](https://books.sonatype.com/nexus-book/reference3/install.html#directories) for reference.

#### 1. Build the plugin
Build and install the into your local maven repository using the following commands:

```
./mvnw install
```

#### 2. Copy all needed jars into nexus system folder
```
cp -ra ~/.m2/repository/com/larscheidschmitzhermes $install-dir/system/com
```

#### 3. Add bundle to startup properties
Append the following line to `$install-dir/etc/karafstartup.properties` 

Please replace _[PLUGIN_VERSION]_ by the current plugin version.
```
mvn\:com.larscheidschmitzhermes/nexus3-github-oauth-plugin/[PLUGIN_VERSION] = 200
```

#### 4. Create githuboauth.properties
Create a `$install-dir/etc/githuboauth.properties`

The file has to contain the following property:

```properties
github.api.url=https://github.example.com/api/v3 #note: no trailing slash!!!
```

#### 5. Restart Nexus
Restart your Nexus instance to let it pick up your changes.

## Setup

#### 1. Activate the Realm
Log in to your nexus and go to _Administration > Security > Realms_. Move the Github Realm to the right. The realm order in the form determines the order of the realms in your authentication flow. We recommend putting Github _after_ the built-in realms.

#### 2. Group / Roles Mapping
When logged in through Github, all organizations and teams the user is a member of will be mapped into roles like so:

_organization name/team name_ e.g. `dummy-org/developers`

You need to manually create these roles in _Administration > Security > Roles_ in order to assign them the desired priviliges. Note that anybody is allowed to login (authenticate) with a valid Github Token from your Github instance, but he/she won't have any priviledges assigned with their teams (authorization).

## Usage

The following steps need to be done by every developer who wants to login to your nexus with Github.
#### 1. Generate OAuth Token
 
In your github account under _Settings > Personal access tokens_ generate a new OAuth token. The only scope you need is **read:org** 

#### 2. Login to nexus

When logging in to nexus, use your github user name as the username and the oauth token you just generated as the password.
This also works through maven, gradle etc.

## Credits

The whole project is heavily influenced by the [nexus3-crowd-plugin](https://github.com/pingunaut/nexus3-crowd-plugin).
