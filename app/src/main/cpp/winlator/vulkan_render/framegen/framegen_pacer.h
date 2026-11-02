// Shared frame-generation pacer: decides how many frames to synthesise for a
// source frame from the measured rates, the multiplier/target and the panel's
// refresh rate. Engine-agnostic; used by both lsfg and cronyfg.
//
// Moved from lsfg_pacer.hpp (Eden Emulator Project / lsfg-vk lineage,
// GPL-3.0-or-later) without behaviour changes.

#pragma once

#include <chrono>
#include <cstddef>
#include <cstdint>
#include <optional>

namespace framegen {

constexpr size_t kMaxMultiplier = 4;

struct Config {
    uint32_t multiplier{2};
    uint32_t target_rate{};
    float refresh_rate{};
};

struct Plan {
    size_t generations{};
    bool warm{};
};

struct Stats {
    float source_rate{};
    float loop_rate{};
    float refresh_rate{};
    float target_rate{};
    float slots{};
    size_t limit{};
    bool rates_settled{};
    uint64_t last_drawn{};
    float last_elapsed{};
    uint64_t source_frames{};
};

class Pacer {
public:
    void SetConfig(const Config& config_) { config = config_; }
    [[nodiscard]] const Config& GetConfig() const { return config; }

    [[nodiscard]] size_t MaxGenerations() const;
    [[nodiscard]] Plan PlanFor(size_t capacity, uint64_t source_frames);
    [[nodiscard]] Stats GetStats() const;

    void Reset();

private:
    using Clock = std::chrono::steady_clock;

    void TrackSourceRate(Clock::time_point now, uint64_t source_frames);
    void TrackLoopRate(float interval_seconds);
    [[nodiscard]] bool RatesSettled() const;
    [[nodiscard]] size_t HeadroomLimit() const;

    Config config;

    std::optional<Clock::time_point> last_frame;
    std::optional<Clock::time_point> last_source_sample;
    uint64_t last_source_frames{};
    float source_interval{};
    float source_frame_accum{};
    float source_time_accum{};
    float loop_interval{};
    uint32_t source_samples{};
    uint32_t loop_samples{};
    uint64_t last_drawn{};
    float last_elapsed{};
    float output_credit{};
    size_t limit{};
};

} // namespace framegen
