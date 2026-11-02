#pragma once
// Пресеты качества CronyFG — единый источник истины.
//
// Почему пресеты, а не одна крутилка: tuning-константы в шейдерах измеряются в
// flow-пикселях (порог round-trip, cost, сила гейта гамута) и НЕ масштабируются
// с разрешением потока. Поэтому один ползунок менял число flow-пикселей, но не
// менял картину по плавности. Каждый пресет задаёт и разрешение потока
// (flowShort), и согласованные с ним пороги доверия, нормализованные от flowShort
// (доли rtLoFrac/rtHiFrac перемножаются на реальное короткое поле на CPU).
//
// Порядок массива = порядок кнопок в UI: 0 эконом .. 5 максимальный.
// Значения — тюнинг-точки: подкручиваются после теста на устройстве.

#include <cstdint>

namespace cronyfg {

struct QualityPreset {
    uint32_t flowShort;        // короткое поле поля потока (L0), px
    float    coarseFirstStep;  // первый шаг coarse-поиска, flow px
    float    costScale;        // refine: множитель cost в conf = 1 - cost*scale
    float    agreeScale;       // refine: множитель agree в conf
    float    rtLoFrac;         // refine: нижний порог round-trip, доля flowShort
    float    rtHiFrac;         // refine: верхний порог round-trip, доля flowShort
    float    rtPenalty;        // refine: насколько окклюзия давит conf (0..1)
    float    gamutStrength;    // interp: сила гейта гамута (0..1)
    float    confFloor;        // interp: нижняя планка useBlend (0..1)
};

// Индекс = порядок кнопок. Логика нарастания: выше пресет -> больше разрешение
// потока и шире поиск (лучше переносит быстрое движение), мягче гейт гамута и
// ниже costScale/agreeScale (сильнее доверие потоку = плавнее), выше допущение
// round-trip-остатка и больше confFloor (меньше схлопа в ближайший реальный кадр).
// Эконом — резко, консервативно, дёшево по GPU; максимальный — максимально плавно,
// дороже.
inline constexpr QualityPreset kCronyfgPresets[6] = {
    /* 0 эконом     */ {160u, 12.0f, 5.0f, 2.5f, 0.012f, 0.045f, 0.90f, 0.85f, 0.00f},
    /* 1 плавно     */ {208u, 14.0f, 4.5f, 2.2f, 0.015f, 0.055f, 0.85f, 0.78f, 0.00f},
    /* 2 баланс     */ {264u, 16.0f, 4.0f, 2.0f, 0.018f, 0.062f, 0.85f, 0.75f, 0.05f},
    /* 3 высокий    */ {320u, 18.0f, 3.5f, 1.8f, 0.020f, 0.075f, 0.80f, 0.70f, 0.10f},
    /* 4 усиленный  */ {416u, 20.0f, 3.0f, 1.6f, 0.024f, 0.090f, 0.75f, 0.62f, 0.15f},
    /* 5 максимальн */ {512u, 22.0f, 2.6f, 1.4f, 0.028f, 0.110f, 0.70f, 0.55f, 0.20f},
};

constexpr uint32_t kCronyfgPresetCount = 6;
constexpr uint32_t kCronyfgDefaultPreset = 2;  // баланс

// Безопасное чтение пресета по индексу (обрезка к диапазону таблицы).
inline const QualityPreset& presetAt(int index) {
    if (index < 0) index = 0;
    if (index >= (int)kCronyfgPresetCount) index = (int)kCronyfgPresetCount - 1;
    return kCronyfgPresets[index];
}

} // namespace cronyfg
