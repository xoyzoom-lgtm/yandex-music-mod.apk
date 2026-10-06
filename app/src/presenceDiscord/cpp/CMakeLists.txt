cmake_minimum_required(VERSION 3.22.1)
project(ymmod_discord LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Пакет из discord_partner_sdk.aar через Prefab (buildFeatures.prefab = true).
find_package(discord_partner_sdk REQUIRED CONFIG)

add_library(ymmod_discord SHARED discord_presence.cpp)
target_link_libraries(ymmod_discord discord_partner_sdk::discord_partner_sdk log)
