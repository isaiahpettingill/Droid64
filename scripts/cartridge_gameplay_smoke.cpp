#include "sysdeps.h"
#include "C64.h"
#include "CPUC64.h"
#include "emu_bindings.h"
#include "keycodes.h"

#include <cstdio>
#include <cstdint>
#include <vector>

extern C64 *TheC64;

static uint64_t frame_hash(const std::vector<unsigned char> &pixels)
{
    uint64_t hash = 14695981039346656037ULL;
    for (size_t i = 0; i < 0x180 * 0x110; ++i) {
        hash = (hash ^ pixels[i]) * 1099511628211ULL;
    }
    return hash;
}

int main(int argc, char **argv)
{
    if (argc != 2) {
        fprintf(stderr, "Usage: cartridge_gameplay_smoke image.crt\n");
        return 2;
    }
    FILE *file = fopen(argv[1], "rb");
    if (!file) return 2;
    fseek(file, 0, SEEK_END);
    long size = ftell(file);
    rewind(file);
    if (size < 0x40) { fclose(file); return 2; }
    std::vector<unsigned char> image(size);
    if (fread(image.data(), 1, size, file) != static_cast<size_t>(size)) {
        fclose(file);
        return 2;
    }
    fclose(file);

    if (emu_init("Emul1541Proc = FALSE\nJoystickSwap = FALSE\n", 0)) return 2;
    if (emu_load(5, image.data(), size, argv[1])) { emu_shutdown(); return 2; }

    std::vector<unsigned char> video(0x180 * 0x110 * 4);
    std::vector<unsigned char> audio(44100 / 50 * 2);
    uint64_t title = 0, after = 0;
    for (int line = 0; line < 550000; ++line) {
        // Wait for the title, then tap joystick port 2 fire for a few frames.
        int fire = (line >= 300000 && line < 301000) ? C64STICK_FIRE : 0;
        if (emu_update(fire, video.data(), audio.data(), 0) < 0) {
            emu_shutdown();
            return 2;
        }
        if (line == 300000) title = frame_hash(video);
        if (line == 549999) after = frame_hash(video);
    }
    MOS6510State state;
    TheC64->TheCPU->GetState(&state);
    printf("%s: title=%016llx after=%016llx pc=%04x bank=%d\n",
           argv[1], static_cast<unsigned long long>(title),
           static_cast<unsigned long long>(after), state.pc,
           TheC64->cartridgeBank);
    emu_shutdown();
    // A game that returned to the title after fire has not passed the check.
    return title == after ? 1 : 0;
}
