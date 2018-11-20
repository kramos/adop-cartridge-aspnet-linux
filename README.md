# Cartridge Skeleton
The purpose of this repository is to define a base cartridge with empty job definitions and pipelines to allow developers to rapidly develop their cartridges.

## Stucture
A cartridge is broken down into the following sections:

 * infra
  * For infrastructure-related items
 * jenkins
  * For Jenkins-related items
 * src
  * For source control-related items

## Metadata
Each cartridge should contain a "metadata.cartridge" file that specifies the following metadata:

 * `CARTRIDGE_SDK_VERSION`
  * This defines the version of the Cartridge SDK that the cartridge conforms to
 
## Prerequisites

 * Make sure [adop-pluggable-scm](https://github.com/Accenture/adop-pluggable-scm) is loaded into your ADOP/C.
 

## Using this Repository
When developing a cartridge it is advisable to make a copy of this repository and remove all of the README.md files so that it serves as a basis for the new cartridge.
