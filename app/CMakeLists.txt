
cmake_minimum_required(VERSION 3.22.1)
project(Droid64 LANGUAGES C CXX)

file( GLOB srcfiles
      "src/main/cpp/*.cpp"
      "src/main/cpp/emu/*.cpp"
      "src/main/cpp/emu/pc/*.cpp"
      "src/main/cpp/emu/rom/*.cpp"
)

add_library( Droid64 SHARED ${srcfiles} )

target_compile_definitions(Droid64 PRIVATE ANDROID FRODO_PC PRECISE_CPU_CYCLES=1 PRECISE_CIA_CYCLES=1 PC_IS_POINTER=0)
target_compile_options(Droid64 PRIVATE -Wno-write-strings)
target_link_options(Droid64 PRIVATE -Wl,-z,max-page-size=16384)
