package it.fabriziodavi.vivovideo.model

import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Stili del video automatico: ognuno è una "regia" con ritmo, transizioni, effetti e titolo propri.
 * Le durate sono quelle senza musica; con la musica i tagli cadono sui battiti ([beatsPerSlot] battiti a scena).
 */
enum class AutoStyle(
    val label: String,
    val description: String,
    val photoUs: Long,
    val videoUs: Long,
    val beatsPerSlot: Int,
    /** Transizioni dei momenti calmi. */
    val transitions: List<TransitionType>,
    val transitionUs: Long,
    val fx: List<ClipFx>,
    val accents: List<ClipFx>,
    val title: TextPreset,
    /** Transizioni d'impatto: sui colpi forti e sui "drop" della musica. */
    val impact: List<TransitionType> = emptyList(),
    /** Effetti a ritmo nelle parti cariche del brano. */
    val beatFx: List<ClipFx> = emptyList(),
    /** Accelerazioni e rallenty a tempo nei video (parti cariche). */
    val ramps: Boolean = false,
    /** Rallenty nei video a 100+ fps (parti calme). */
    val slowmo: Boolean = false,
    /** Look colore del tema (come un colorista), uguale su tutte le scene. */
    val grade: Grade = Grade(),
    /** Intro a raffica: lampi di scene su mezzi battiti, poi il titolo sul colpo. */
    val intro: Boolean = false,
    /** Momento eroe sul drop: rallenty al centro della scena, zoom e colpo. */
    val hero: Boolean = false,
    /** Ogni quanti video (nelle parti cariche) c'è un'accelerazione a tempo. */
    val rampEvery: Int = 2,
    /** Curve di velocità preferite per le accelerazioni. */
    val rampStyles: List<SpeedPreset> = listOf(SpeedPreset.MONTAGE, SpeedPreset.BULLET, SpeedPreset.FLASH_IN, SpeedPreset.HERO),
    /** Data della videocamera (arancione, in basso a destra) dalla data vera di foto e video. */
    val dateStamp: Boolean = false,
    /** Le foto "respirano" a tempo anche nelle parti calme: un piccolo zoom ogni [popEvery] battiti. */
    val gentlePops: Boolean = false,
    val popEvery: Int = 1,
    val popScale: Float = 0.07f,
    /** Stile della data sulle scene (videocamera arancione o tappa di viaggio). */
    val stamp: TextPreset = TextPreset.DATA,
    /** Durata minima di una scena (temi calmi): con brani veloci si usano più battiti per scena. */
    val minSceneUs: Long = 0,
    /** Nelle parti cariche del brano le scene si accorciano (i temi delicati restano distesi). */
    val shorterOnEnergy: Boolean = true,
) {
    CINEMA(
        "Cinema", "Solenne: look da film, messa a fuoco del titolo, dissolvenze di luce e pellicola, rallenty sui momenti calmi.",
        photoUs = 3_500_000, videoUs = 4_000_000, beatsPerSlot = 4,
        transitions = listOf(TransitionType.CROSSFADE, TransitionType.CROSSFADE, TransitionType.LUMA, TransitionType.LEAK, TransitionType.BURN),
        transitionUs = 1_000_000,
        fx = listOf(ClipFx(FxType.CINEMATIC, 0.8f), ClipFx(FxType.FILM35, 0.35f)),
        accents = listOf(ClipFx(FxType.RAYS, 0.4f), ClipFx(FxType.SWEEP, 0.4f)),
        title = TextPreset.CINEMA26,
        impact = listOf(TransitionType.PUNCH, TransitionType.FLASH, TransitionType.CUBE),
        beatFx = listOf(ClipFx(FxType.ZOOMBEAT, 0.35f)),
        slowmo = true,
        grade = Grade(contrast = 0.12f, saturation = -0.05f, shadowWheel = Wheel(195f, 0.35f), highWheel = Wheel(32f, 0.3f)),
        intro = true,
        minSceneUs = 2_400_000,
    ),
    ENERGICO(
        "Energico", "Ritmo serrato: tagli su ogni colpo nelle parti cariche, zoom punch, schiaffi RGB, scosse e accelerazioni.",
        photoUs = 1_500_000, videoUs = 1_800_000, beatsPerSlot = 2,
        transitions = listOf(TransitionType.ZOOM, TransitionType.WHIP, TransitionType.SWIRL, TransitionType.SLIDE_UP, TransitionType.PRISM),
        transitionUs = 350_000,
        fx = listOf(ClipFx(FxType.HIT, 0.5f), ClipFx(FxType.VIVID, 0.35f)),
        accents = listOf(ClipFx(FxType.SPEEDLINES, 0.5f), ClipFx(FxType.RGBBEAT, 0.6f)),
        title = TextPreset.ESPLOSIONE,
        impact = listOf(TransitionType.PUNCH, TransitionType.RGB, TransitionType.GLITCH, TransitionType.FLASH, TransitionType.SHAKE),
        beatFx = listOf(ClipFx(FxType.ZOOMBEAT, 0.6f), ClipFx(FxType.SHAKEBEAT, 0.45f)),
        ramps = true,
        grade = Grade(contrast = 0.22f, vibrance = 0.3f, shadows = -0.1f),
        intro = true, hero = true,
    ),
    VIRALE(
        "Reel virale", "Da social: intro che sale, sul drop un taglio a ogni battito, velocità a scatti, duotono e lampi di luce.",
        photoUs = 1_200_000, videoUs = 1_500_000, beatsPerSlot = 2,
        transitions = listOf(TransitionType.WHIP, TransitionType.SLIDE_UP, TransitionType.ZOOM, TransitionType.CUBE),
        transitionUs = 300_000,
        fx = listOf(ClipFx(FxType.VIVID, 0.45f)),
        accents = listOf(ClipFx(FxType.DUOPOP, 0.55f), ClipFx(FxType.SWEEP, 0.6f), ClipFx(FxType.SPLIT3, 1f)),
        title = TextPreset.FESTA,
        impact = listOf(TransitionType.PUNCH, TransitionType.RGB, TransitionType.FLASH, TransitionType.SWIRL),
        beatFx = listOf(ClipFx(FxType.ZOOMBEAT, 0.7f), ClipFx(FxType.RGBBEAT, 0.4f)),
        ramps = true,
        grade = Grade(contrast = 0.18f, saturation = 0.12f, vibrance = 0.35f, exposure = 0.05f),
        intro = true, hero = true,
    ),
    RICORDI(
        "Ricordi", "Nostalgico: pellicola 35 mm, luce calda, bruciature di pellicola, passaggi morbidi.",
        photoUs = 3_000_000, videoUs = 3_000_000, beatsPerSlot = 4,
        transitions = listOf(TransitionType.CROSSFADE, TransitionType.CROSSFADE, TransitionType.BURN, TransitionType.LEAK, TransitionType.LUMA),
        transitionUs = 900_000,
        fx = listOf(ClipFx(FxType.FILM35, 0.6f), ClipFx(FxType.LIGHTLEAK, 0.3f), ClipFx(FxType.VIGNETTE, 0.5f)),
        accents = listOf(ClipFx(FxType.BOKEH, 0.35f), ClipFx(FxType.DREAMY, 0.4f)),
        title = TextPreset.CASCATA,
        impact = listOf(TransitionType.FLASH, TransitionType.BURN),
        slowmo = true,
        grade = Grade(contrast = -0.12f, shadows = 0.25f, saturation = -0.12f, temperature = 0.35f, midWheel = Wheel(35f, 0.2f)),
        minSceneUs = 2_600_000, shorterOnEnergy = false,
    ),
    SOGNO(
        "Sogno", "Morbido e luminoso: alone di luce, riflessi iridescenti, dissolvenze di luce, titolo che va a fuoco.",
        photoUs = 3_200_000, videoUs = 3_400_000, beatsPerSlot = 4,
        transitions = listOf(TransitionType.CROSSFADE, TransitionType.LUMA, TransitionType.LIQUID, TransitionType.LEAK),
        transitionUs = 1_000_000,
        fx = listOf(ClipFx(FxType.DREAMY, 0.55f), ClipFx(FxType.VIVID, 0.15f)),
        accents = listOf(ClipFx(FxType.HOLO, 0.4f), ClipFx(FxType.SPARKLE, 0.45f)),
        title = TextPreset.SOGNO,
        impact = listOf(TransitionType.LUMA, TransitionType.SWIRL, TransitionType.FLASH),
        beatFx = listOf(ClipFx(FxType.ZOOMBEAT, 0.25f)),
        slowmo = true,
        grade = Grade(exposure = 0.12f, contrast = -0.15f, highlights = -0.2f, saturation = -0.08f, tint = 0.12f, highWheel = Wheel(330f, 0.15f)),
        minSceneUs = 3_000_000, shorterOnEnergy = false,
    ),
    ELEGANTE(
        "Elegante", "Luminoso e raffinato: colori puliti, luci bokeh e polvere d'oro, cubo 3D, titolo dorato.",
        photoUs = 3_000_000, videoUs = 3_200_000, beatsPerSlot = 4,
        transitions = listOf(TransitionType.CROSSFADE, TransitionType.CROSSFADE, TransitionType.CUBE, TransitionType.LEAK, TransitionType.IRIS),
        transitionUs = 800_000,
        fx = listOf(ClipFx(FxType.VIVID, 0.25f), ClipFx(FxType.GLOW, 0.3f)),
        accents = listOf(ClipFx(FxType.SPARKLE, 0.5f), ClipFx(FxType.SWEEP, 0.45f)),
        title = TextPreset.ORO,
        impact = listOf(TransitionType.PUNCH, TransitionType.FLASH, TransitionType.LUMA),
        beatFx = listOf(ClipFx(FxType.ZOOMBEAT, 0.3f)),
        slowmo = true,
        grade = Grade(exposure = 0.06f, contrast = 0.06f, highlights = -0.15f, vibrance = 0.15f, highWheel = Wheel(45f, 0.12f)),
        minSceneUs = 2_600_000, shorterOnEnergy = false,
    ),
    VIAGGIO(
        "Viaggio", "Dinamico: scorrimenti con scia, vortici e cubi 3D, colori vivaci, raggi di luce, accelerazioni a tempo.",
        photoUs = 2_200_000, videoUs = 2_600_000, beatsPerSlot = 2,
        transitions = listOf(TransitionType.WHIP, TransitionType.ZOOM, TransitionType.CUBE, TransitionType.CROSSFADE, TransitionType.SLIDE_UP),
        transitionUs = 500_000,
        fx = listOf(ClipFx(FxType.VIVID, 0.45f)),
        accents = listOf(ClipFx(FxType.RAYS, 0.45f), ClipFx(FxType.SWEEP, 0.5f)),
        title = TextPreset.TITOLO3D,
        impact = listOf(TransitionType.PUNCH, TransitionType.SWIRL, TransitionType.SPIN, TransitionType.FLASH),
        beatFx = listOf(ClipFx(FxType.ZOOMBEAT, 0.45f)),
        ramps = true,
        grade = Grade(contrast = 0.1f, vibrance = 0.3f, temperature = 0.12f),
        intro = true,
        minSceneUs = 1_200_000,
    ),
    BLOCKBUSTER(
        "Blockbuster", "Trailer da cinema: intro a raffica, teal & orange, bande cinema, bagliori anamorfici, esplosioni di luce e momento eroe sul drop.",
        photoUs = 2_000_000, videoUs = 2_400_000, beatsPerSlot = 2,
        transitions = listOf(TransitionType.CROSSFADE, TransitionType.LENS, TransitionType.LUMA, TransitionType.ZOOM),
        transitionUs = 600_000,
        fx = listOf(ClipFx(FxType.LETTERBOX, 1f), ClipFx(FxType.FILM35, 0.3f)),
        accents = listOf(ClipFx(FxType.FLARE, 0.6f), ClipFx(FxType.RAYS, 0.4f)),
        title = TextPreset.CINEMA26,
        impact = listOf(TransitionType.PUNCH, TransitionType.BURST, TransitionType.SHUTTER, TransitionType.TEAR),
        beatFx = listOf(ClipFx(FxType.ZOOMBEAT, 0.5f), ClipFx(FxType.SHAKEBEAT, 0.3f)),
        ramps = true, slowmo = true,
        grade = Grade(contrast = 0.22f, saturation = 0.05f, shadows = -0.1f, shadowWheel = Wheel(190f, 0.5f), highWheel = Wheel(30f, 0.45f)),
        intro = true, hero = true,
        minSceneUs = 1_000_000,
    ),
    NEON(
        "Neon notte", "Notturno elettrico: magenta e ciano, glitch, schiaffi RGB, strisce e riflessi olografici a tempo.",
        photoUs = 1_600_000, videoUs = 1_900_000, beatsPerSlot = 2,
        transitions = listOf(TransitionType.STRIPES, TransitionType.SLIDE_UP, TransitionType.PIXELATE, TransitionType.GLITCH),
        transitionUs = 350_000,
        fx = listOf(ClipFx(FxType.GLOW, 0.35f)),
        accents = listOf(ClipFx(FxType.HOLO, 0.5f), ClipFx(FxType.NEON, 0.5f), ClipFx(FxType.RGBBEAT, 0.6f)),
        title = TextPreset.INSEGNA,
        impact = listOf(TransitionType.RGB, TransitionType.GLITCH, TransitionType.BURST, TransitionType.SHUTTER),
        beatFx = listOf(ClipFx(FxType.RGBBEAT, 0.6f), ClipFx(FxType.ZOOMBEAT, 0.4f)),
        ramps = true,
        grade = Grade(contrast = 0.25f, saturation = 0.2f, temperature = -0.45f, tint = 0.35f, shadowWheel = Wheel(260f, 0.4f), highWheel = Wheel(310f, 0.3f)),
        intro = true, hero = true,
    ),
    ESTATE(
        "Estate pop", "Solare e colorato: luce piena, duotono pop, lampi di luce, scorrimenti e titoli che rimbalzano.",
        photoUs = 1_800_000, videoUs = 2_000_000, beatsPerSlot = 2,
        transitions = listOf(TransitionType.SLIDE_UP, TransitionType.WHIP, TransitionType.CUBE, TransitionType.STRIPES),
        transitionUs = 400_000,
        fx = listOf(ClipFx(FxType.VIVID, 0.35f)),
        accents = listOf(ClipFx(FxType.DUOPOP, 0.4f), ClipFx(FxType.SWEEP, 0.55f), ClipFx(FxType.SPLIT3, 1f)),
        title = TextPreset.FESTA,
        impact = listOf(TransitionType.PUNCH, TransitionType.FLASH, TransitionType.SWIRL, TransitionType.BURST),
        beatFx = listOf(ClipFx(FxType.ZOOMBEAT, 0.5f)),
        ramps = true,
        grade = Grade(exposure = 0.12f, contrast = 0.08f, saturation = 0.2f, vibrance = 0.35f, temperature = 0.25f),
        intro = true,
    ),
    AZIONE(
        "Azione", "Sport e avventura: tagli secchi, scosse sui colpi, linee di velocità, accelerazioni e rallenty da eroe.",
        photoUs = 1_400_000, videoUs = 1_700_000, beatsPerSlot = 2,
        transitions = listOf(TransitionType.WHIP, TransitionType.ZOOM, TransitionType.LENS, TransitionType.SLIDE_UP),
        transitionUs = 300_000,
        fx = listOf(ClipFx(FxType.VIVID, 0.25f)),
        accents = listOf(ClipFx(FxType.SPEEDLINES, 0.55f), ClipFx(FxType.SHAKEBEAT, 0.5f)),
        title = TextPreset.ESPLOSIONE,
        impact = listOf(TransitionType.SPEEDZOOM, TransitionType.PUNCH, TransitionType.SHAKE, TransitionType.TEAR, TransitionType.SMASH),
        beatFx = listOf(ClipFx(FxType.SHAKEBEAT, 0.5f), ClipFx(FxType.ZOOMBEAT, 0.5f)),
        ramps = true,
        grade = Grade(contrast = 0.3f, saturation = 0.1f, highlights = -0.15f, shadowWheel = Wheel(200f, 0.3f)),
        intro = true, hero = true,
    ),
    ESTREMO(
        "Estremo", "Bike, MTB, corsa, GoPro: accelerazioni a proiettile su ogni video, impatti sui colpi, scatti di velocità e sfondamenti sul drop.",
        photoUs = 1_200_000, videoUs = 1_500_000, beatsPerSlot = 2,
        transitions = listOf(TransitionType.WHIP, TransitionType.SPEEDZOOM, TransitionType.LENS, TransitionType.SLIDE_UP, TransitionType.ZOOM),
        transitionUs = 280_000,
        fx = listOf(ClipFx(FxType.VIVID, 0.3f)),
        accents = listOf(ClipFx(FxType.SPEEDLINES, 0.6f), ClipFx(FxType.FISHEYE, 0.25f), ClipFx(FxType.RGBBEAT, 0.5f)),
        title = TextPreset.ESPLOSIONE,
        impact = listOf(TransitionType.SMASH, TransitionType.SPEEDZOOM, TransitionType.PUNCH, TransitionType.TEAR, TransitionType.SHAKE),
        beatFx = listOf(ClipFx(FxType.IMPACT, 0.6f)),
        ramps = true, slowmo = true,
        grade = Grade(contrast = 0.35f, saturation = 0.12f, vibrance = 0.2f, highlights = -0.2f, shadows = -0.08f, shadowWheel = Wheel(205f, 0.35f), highWheel = Wheel(40f, 0.2f)),
        intro = true, hero = true,
        rampEvery = 1,
        rampStyles = listOf(SpeedPreset.BULLET, SpeedPreset.FLASH_IN, SpeedPreset.MONTAGE),
    ),
    ROMANTICO(
        "Romantico", "Luce soffusa e calda, petali e cuori di luce, chiusure a cuore e dissolvenze bokeh, rallenty, titolo in corsivo che va a fuoco.",
        photoUs = 3_200_000, videoUs = 3_400_000, beatsPerSlot = 4,
        transitions = listOf(TransitionType.CROSSFADE, TransitionType.BOKEHFADE, TransitionType.LUMA, TransitionType.CROSSFADE, TransitionType.HEART),
        transitionUs = 1_000_000,
        fx = listOf(ClipFx(FxType.PROMIST, 0.55f)),
        accents = listOf(ClipFx(FxType.PETALS, 0.5f), ClipFx(FxType.HEARTS, 0.45f), ClipFx(FxType.SWEEP, 0.35f)),
        title = TextPreset.AMORE,
        impact = listOf(TransitionType.BOKEHFADE, TransitionType.FLASH, TransitionType.HEART),
        beatFx = listOf(ClipFx(FxType.GLOWBEAT, 0.5f)),
        slowmo = true,
        grade = Grade(exposure = 0.08f, contrast = -0.1f, highlights = -0.15f, saturation = -0.05f, temperature = 0.25f, tint = 0.1f, highWheel = Wheel(20f, 0.15f), shadowWheel = Wheel(330f, 0.12f)),
        gentlePops = true, popEvery = 4, popScale = 0.03f,
        minSceneUs = 3_000_000, shorterOnEnergy = false,
    ),
    MATRIMONIO(
        "Matrimonio", "Elegante e luminoso: bianchi puliti e oro, luce soffusa, lampi di luce e polvere d'oro, passaggi lenti e solenni.",
        photoUs = 3_500_000, videoUs = 3_800_000, beatsPerSlot = 4,
        transitions = listOf(TransitionType.CROSSFADE, TransitionType.CROSSFADE, TransitionType.LUMA, TransitionType.BOKEHFADE, TransitionType.CUBE),
        transitionUs = 1_100_000,
        fx = listOf(ClipFx(FxType.PROMIST, 0.45f), ClipFx(FxType.LETTERBOX, 1f)),
        accents = listOf(ClipFx(FxType.SPARKLE, 0.45f), ClipFx(FxType.SWEEP, 0.45f), ClipFx(FxType.HEARTS, 0.3f)),
        title = TextPreset.ORO,
        impact = listOf(TransitionType.FLASH, TransitionType.LUMA, TransitionType.BOKEHFADE),
        beatFx = listOf(ClipFx(FxType.GLOWBEAT, 0.4f)),
        slowmo = true,
        grade = Grade(exposure = 0.1f, contrast = 0.04f, highlights = -0.2f, saturation = -0.08f, temperature = 0.12f, highWheel = Wheel(42f, 0.15f)),
        gentlePops = true, popEvery = 4, popScale = 0.025f,
        minSceneUs = 3_200_000, shorterOnEnergy = false,
    ),
    SUPER8(
        "Super 8", "Le pellicole di una volta, ma moderne: immagine che balla, colori caldi sbiaditi, bordo con i fori, proiettore, salti di pellicola e data della videocamera.",
        photoUs = 2_600_000, videoUs = 2_800_000, beatsPerSlot = 4,
        transitions = listOf(TransitionType.FILMSLIP, TransitionType.BURN, TransitionType.CROSSFADE, TransitionType.LEAK),
        transitionUs = 600_000,
        fx = listOf(ClipFx(FxType.SUPER8, 0.8f), ClipFx(FxType.FILMFRAME, 1f)),
        accents = listOf(ClipFx(FxType.PROJECTOR, 0.5f), ClipFx(FxType.LIGHTLEAK, 0.45f), ClipFx(FxType.DUST, 0.5f)),
        title = TextPreset.DIARIO,
        impact = listOf(TransitionType.FILMSLIP, TransitionType.BURN, TransitionType.FLASH),
        beatFx = listOf(ClipFx(FxType.GLOWBEAT, 0.35f)),
        grade = Grade(contrast = -0.05f, shadows = 0.15f, saturation = -0.1f, temperature = 0.3f),
        dateStamp = true, gentlePops = true, popEvery = 2, popScale = 0.04f,
        minSceneUs = 2_200_000, shorterOnEnergy = false,
    ),
    ANNI90(
        "Anni '90", "Videocamera di famiglia anni '90: VHS, riavvolgimenti, disturbi a tempo, colori saturi un po' sbavati e la data arancione.",
        photoUs = 2_200_000, videoUs = 2_400_000, beatsPerSlot = 2,
        transitions = listOf(TransitionType.REWIND, TransitionType.GLITCH, TransitionType.SLIDE_UP, TransitionType.STRIPES),
        transitionUs = 450_000,
        fx = listOf(ClipFx(FxType.VHS, 0.6f)),
        accents = listOf(ClipFx(FxType.RGBBEAT, 0.5f), ClipFx(FxType.CHROMA, 0.35f)),
        title = TextPreset.DIARIO,
        impact = listOf(TransitionType.REWIND, TransitionType.GLITCH, TransitionType.RGB),
        beatFx = listOf(ClipFx(FxType.RGBBEAT, 0.45f), ClipFx(FxType.ZOOMBEAT, 0.3f)),
        ramps = true,
        grade = Grade(contrast = 0.1f, saturation = 0.18f, temperature = 0.1f, tint = 0.08f),
        intro = true, dateStamp = true,
    ),
    NATURA(
        "Natura", "Verdi e azzurri ricchi, foschia leggera, raggi di sole, scene lunghe e passaggi morbidi; rallenty e foto che respirano.",
        photoUs = 3_400_000, videoUs = 3_600_000, beatsPerSlot = 4,
        transitions = listOf(TransitionType.CROSSFADE, TransitionType.CROSSFADE, TransitionType.LUMA, TransitionType.LIQUID, TransitionType.LEAK),
        transitionUs = 1_100_000,
        fx = listOf(ClipFx(FxType.SKYGRAD, 0.35f)),
        accents = listOf(ClipFx(FxType.HAZE, 0.4f), ClipFx(FxType.RAYS, 0.35f), ClipFx(FxType.DREAMY, 0.25f)),
        title = TextPreset.ELEGANTE,
        impact = listOf(TransitionType.LUMA, TransitionType.BOKEHFADE, TransitionType.LIQUID),
        beatFx = listOf(ClipFx(FxType.GLOWBEAT, 0.25f)),
        slowmo = true,
        grade = Grade(contrast = 0.08f, shadows = 0.1f, vibrance = 0.3f, temperature = 0.08f,
            hsl = List(8) { if (it == HslBand.GREEN.ordinal) Hsl(hue = -0.15f, sat = 0.15f, lum = -0.1f) else if (it == HslBand.BLUE.ordinal) Hsl(sat = 0.2f, lum = -0.1f) else Hsl() }),
        gentlePops = true, popEvery = 4, popScale = 0.03f,
        minSceneUs = 3_200_000, shorterOnEnergy = false,
    ),
    MARE(
        "Mare", "Turchesi e luce calda d'estate: riflessi d'acqua, scorrimenti, transizioni liquide e lampi di sole a tempo.",
        photoUs = 2_400_000, videoUs = 2_600_000, beatsPerSlot = 2,
        transitions = listOf(TransitionType.LIQUID, TransitionType.SLIDE_UP, TransitionType.WHIP, TransitionType.CROSSFADE, TransitionType.BOKEHFADE),
        transitionUs = 600_000,
        fx = listOf(ClipFx(FxType.VIVID, 0.25f)),
        accents = listOf(ClipFx(FxType.WATER, 0.5f), ClipFx(FxType.SWEEP, 0.5f), ClipFx(FxType.FLARE, 0.4f)),
        title = TextPreset.FESTA,
        impact = listOf(TransitionType.PUNCH, TransitionType.FLASH, TransitionType.SWIRL),
        beatFx = listOf(ClipFx(FxType.ZOOMBEAT, 0.4f)),
        ramps = true, slowmo = true,
        grade = Grade(exposure = 0.08f, contrast = 0.1f, vibrance = 0.3f, temperature = 0.15f,
            hsl = List(8) { if (it == HslBand.AQUA.ordinal) Hsl(sat = 0.35f, lum = 0.05f) else if (it == HslBand.BLUE.ordinal) Hsl(hue = -0.2f, sat = 0.25f) else Hsl() }),
        intro = true,
        minSceneUs = 1_400_000,
    ),
    MONTAGNA(
        "Montagna", "Aria tersa e cielo drammatico, bagliori sulle vette, cubi 3D e lenti, accelerazioni sui panorami e momento eroe sul drop.",
        photoUs = 2_600_000, videoUs = 2_900_000, beatsPerSlot = 2,
        transitions = listOf(TransitionType.CROSSFADE, TransitionType.CUBE, TransitionType.LENS, TransitionType.WHIP, TransitionType.LUMA),
        transitionUs = 700_000,
        fx = listOf(ClipFx(FxType.SKYGRAD, 0.55f)),
        accents = listOf(ClipFx(FxType.FLARE, 0.45f), ClipFx(FxType.HAZE, 0.35f), ClipFx(FxType.RAYS, 0.35f)),
        title = TextPreset.CINEMA26,
        impact = listOf(TransitionType.PUNCH, TransitionType.SPEEDZOOM, TransitionType.BURST),
        beatFx = listOf(ClipFx(FxType.ZOOMBEAT, 0.4f)),
        ramps = true, slowmo = true,
        grade = Grade(contrast = 0.18f, highlights = -0.2f, shadows = 0.08f, vibrance = 0.25f, temperature = -0.08f, shadowWheel = Wheel(210f, 0.2f)),
        intro = true, hero = true,
        minSceneUs = 1_800_000,
    ),
    DIARIO(
        "Diario di viaggio", "Vlog moderno: la data di ogni tappa, scorrimenti e zoom puliti, colori vivaci ma naturali, foto che respirano a tempo.",
        photoUs = 2_200_000, videoUs = 2_500_000, beatsPerSlot = 2,
        transitions = listOf(TransitionType.WHIP, TransitionType.SLIDE_UP, TransitionType.ZOOM, TransitionType.CROSSFADE, TransitionType.CUBE),
        transitionUs = 450_000,
        fx = listOf(ClipFx(FxType.VIVID, 0.2f)),
        accents = listOf(ClipFx(FxType.SWEEP, 0.45f), ClipFx(FxType.FLARE, 0.35f)),
        title = TextPreset.TITOLO3D,
        impact = listOf(TransitionType.PUNCH, TransitionType.FLASH, TransitionType.SPEEDZOOM),
        beatFx = listOf(ClipFx(FxType.ZOOMBEAT, 0.35f)),
        ramps = true,
        grade = Grade(contrast = 0.1f, vibrance = 0.25f, temperature = 0.06f),
        intro = true, dateStamp = true, gentlePops = true, popEvery = 2, popScale = 0.035f,
        stamp = TextPreset.TAPPA,
        minSceneUs = 1_500_000,
    );

    /** Durata media di una scena, per scegliere quanti elementi usare. */
    fun averageSlotUs(videoShare: Float): Long = (photoUs * (1 - videoShare) + videoUs * videoShare).toLong()
}

/** Un elemento della galleria candidato al video automatico. */
data class AutoItem(val isVideo: Boolean, val durationUs: Long, val dateMs: Long)

@Serializable
data class AutoOptions(
    val style: AutoStyle = AutoStyle.CINEMA,
    /** Durata desiderata (0 = usa tutto quello che è stato scelto). */
    val targetUs: Long = 60_000_000,
    val title: String = "",
    val subtitle: String = "",
    val seed: Int = 7,
    /** 0 = prima proposta; dalle successive cambiano anche le scene scelte e il punto dei video. */
    val variant: Int = 0,
    /** Formato del video: AUTO = l'orientamento più frequente fra foto e video scelti. */
    val format: VideoFormat = VideoFormat.AUTO,
    /** Regia intelligente: momenti migliori dei video e, nell'orientamento opposto, inquadratura che segue i volti. */
    val smart: Boolean = true,
    /** Intro a raffica (se lo stile la prevede e c'è la musica). */
    val intro: Boolean = true,
)

/** Un elemento della galleria da cui è nato il video automatico. */
@Serializable
data class AutoSource(
    val uri: String, val isVideo: Boolean, val durationUs: Long, val dateMs: Long,
    val width: Int = 0, val height: Int = 0,
) {
    fun item() = AutoItem(isVideo, durationUs, dateMs)
    val orientation: Orientation? get() = if (width <= 0 || height <= 0) null else orientationOf(width, height)
}

/**
 * Come è nato un video automatico: serve per chiedere altre proposte. [imported] = clip già importate
 * (per indirizzo originale), così le proposte successive non rileggono le stesse foto.
 */
@Serializable
data class AutoRecipe(
    val sources: List<AutoSource>,
    val options: AutoOptions,
    val music: MusicTrack? = null,
    val round: Int = 1,
    val imported: Map<String, Clip> = emptyMap(),
)

object AutoEdit {
    /** Al massimo tanti elementi, per non importare intere gallerie. */
    const val MAX_ITEMS = 300

    /**
     * Quali elementi usare per stare nella durata: indici in ordine di data, presi a intervalli regolari
     * (così il video racconta tutto il periodo, non solo l'inizio).
     */
    fun pick(items: List<AutoItem>, style: AutoStyle, targetUs: Long, variant: Int = 0, slotUs: Long? = null): List<Int> {
        if (items.isEmpty()) return emptyList()
        val order = items.indices.sortedBy { items[it].dateMs }
        val videoShare = items.count { it.isVideo }.toFloat() / items.size
        // Con la musica la scena dura quanto i battiti scelti dallo stile: si conta con quella.
        val slot = slotUs?.takeIf { it > 0 } ?: style.averageSlotUs(videoShare)
        val wanted = if (targetUs <= 0) MAX_ITEMS
            else (targetUs / slot.coerceAtLeast(1)).toInt().coerceAtLeast(2)
        val n = minOf(wanted, order.size, MAX_ITEMS)
        if (n >= order.size) return order
        if (variant == 0 || n < 3) return (0 until n).map { k -> order[(k.toLong() * (order.size - 1) / (n - 1).coerceAtLeast(1)).toInt()] }.distinct()
        // Altre proposte: primo e ultimo restano, in mezzo si sceglie a caso dentro ogni tratto del periodo.
        val rnd = Random(variant * 7919 + 13)
        val size = order.size
        return (0 until n).map { k ->
            when (k) {
                0 -> order.first()
                n - 1 -> order.last()
                else -> {
                    val lo = (k.toLong() * size / n).toInt().coerceIn(1, size - 2)
                    val hi = ((k + 1).toLong() * size / n).toInt().coerceIn(lo + 1, size - 1)
                    order[rnd.nextInt(lo, hi)]
                }
            }
        }.distinct()
    }

    /**
     * Monta il video: [sources] sono le clip importate in ordine di data, [music] il brano (facoltativo,
     * con i battiti già trovati). Restituisce il progetto pronto, tutto ritoccabile nell'editor.
     */
    /**
     * Foto e video nello stesso verso del video finale: mescolare orizzontali e verticali rende male.
     * Si tengono quelli nel verso giusto (o di misura ignota); gli altri solo se senza non bastano per [wanted] scene.
     */
    fun preferOrientation(items: List<AutoSource>, format: VideoFormat, wanted: Int): List<AutoSource> {
        val known = items.mapNotNull { it.orientation }
        val out = when (format) {
            VideoFormat.VERTICAL -> Orientation.PORTRAIT
            VideoFormat.HORIZONTAL -> Orientation.LANDSCAPE
            VideoFormat.SQUARE -> return items
            VideoFormat.AUTO -> if (known.isEmpty()) return items else
                if (known.count { it == Orientation.PORTRAIT } > known.count { it == Orientation.LANDSCAPE }) Orientation.PORTRAIT else Orientation.LANDSCAPE
        }
        val same = items.filter { it.orientation == null || it.orientation == out || it.orientation == Orientation.SQUARE }
        return if (same.size >= (wanted * 0.75).toInt().coerceAtLeast(2)) same else items
    }

    /**
     * Raffiche: foto scattate a pochi secondi l'una dall'altra sono quasi uguali. Si tiene solo la migliore
     * di ogni gruppo (secondo [score]; senza punteggio, quella di mezzo). I video restano tutti.
     */
    fun dedupeBursts(items: List<AutoSource>, score: (AutoSource) -> Float?, gapMs: Long = 4_000): List<AutoSource> {
        val sorted = items.sortedBy { it.dateMs }
        val out = mutableListOf<AutoSource>()
        var group = mutableListOf<AutoSource>()
        fun flush() {
            if (group.isEmpty()) return
            out += if (group.size == 1) group[0] else group.maxByOrNull { score(it) ?: -1f }?.takeIf { score(it) != null } ?: group[group.size / 2]
            group = mutableListOf()
        }
        for (it in sorted) {
            if (it.isVideo) { out += it; continue }
            val last = group.lastOrNull()
            if (last != null && it.dateMs - last.dateMs > gapMs) flush()
            group += it
        }
        flush()
        return out
    }

    /** Durata di una scena con la musica (in battiti dello stile), o null se senza musica o con battiti strani. */
    fun beatSlotUs(style: AutoStyle, music: MusicTrack?): Long? {
        val beats = music?.beatsUs.orEmpty().filter { it > 0 }
        val period = beats.zipWithNext { a, b -> b - a }.sorted().let { if (it.isEmpty()) 0L else it[it.size / 2] }
        var bps = style.beatsPerSlot
        if (style.minSceneUs > 0 && period > 0) while (period * bps < style.minSceneUs && bps < 32) bps++
        return (period * bps).takeIf { it in 500_000L..6_000_000L }
    }

    /**
     * Porta la durata totale a [targetUs]: toglie le scene in più, oppure allunga quelle che possono
     * (di battiti interi se c'è il ritmo [periodUs]), e l'ultima finisce esattamente sul bersaglio.
     */
    internal fun fit(clips: MutableList<Clip>, targetUs: Long, periodUs: Long, minSlot: Long) {
        if (clips.isEmpty() || targetUs <= 0) return
        // Tutto in durata sul video finale: con rallenty o accelerazioni il materiale usato è diverso.
        fun ratio(c: Clip) = if (c.isPhoto || c.trimmedUs <= 0) 1.0 else c.outputUs.toDouble() / c.trimmedUs
        fun maxOut(c: Clip): Long = if (c.isPhoto) Clip.PHOTO_MAX_US else (c.sourceDurationUs * ratio(c)).toLong()
        fun withOut(c: Clip, out: Long): Clip {
            if (c.isPhoto) {
                val k = out.toDouble() / c.trimmedUs.coerceAtLeast(1)
                return c.copy(trimStartUs = 0, trimEndUs = out, keyframes = c.keyframes.map { it.copy(timeUs = (it.timeUs * k).toLong()) })
            }
            val len = (out / ratio(c)).toLong().coerceIn(1, c.sourceDurationUs)
            val start = c.trimStartUs.coerceAtMost((c.sourceDurationUs - len).coerceAtLeast(0))
            return c.copy(trimStartUs = start, trimEndUs = start + len)
        }
        var total = clips.sumOf { it.outputUs }
        // Troppo lungo: via le scene oltre il bersaglio, l'ultima si accorcia.
        while (clips.size > 1 && total - clips.last().outputUs >= targetUs) { total -= clips.last().outputUs; clips.removeAt(clips.lastIndex) }
        // Troppo corto: si allunga in proporzione, a battiti interi.
        var deficit = targetUs - total
        if (deficit > 0) {
            repeat(3) {
                if (deficit <= 0) return@repeat
                val room = clips.map { (maxOut(it) - it.outputUs).coerceAtLeast(0) }
                val roomSum = room.sum()
                if (roomSum <= 0) return@repeat
                for (i in clips.indices) {
                    if (deficit <= 0) break
                    var add = minOf(room[i], deficit * room[i] / roomSum + 1)
                    if (periodUs > 0) add = add / periodUs * periodUs
                    add = if (periodUs > 0) minOf(add, deficit / periodUs * periodUs) else minOf(add, deficit, room[i])
                    if (add <= 0) continue
                    val before = clips[i].outputUs
                    clips[i] = withOut(clips[i], before + add)
                    deficit -= clips[i].outputUs - before
                }
            }
        }
        // Se all'ultima resterebbe un pezzetto, la si toglie e il tempo va alla precedente.
        if (clips.size > 1 && targetUs - clips.dropLast(1).sumOf { it.outputUs } < minSlot) clips.removeAt(clips.lastIndex)
        // Il resto (meno di un battito per scena) lo prende l'ultima, che finisce sul bersaglio: i tagli prima restano a tempo.
        val before = clips.dropLast(1).sumOf { it.outputUs }
        val lastLen = (targetUs - before).coerceAtMost(maxOut(clips.last()))
        if (lastLen >= minOf(minSlot, maxOut(clips.last()))) {
            // La curva di velocità arrotonda a passi: si corregge finché la fine cade sul bersaglio.
            var c = withOut(clips.last(), lastLen)
            repeat(5) { val err = targetUs - before - c.outputUs; if (kotlin.math.abs(err) > 2_000) c = withOut(c, (c.outputUs + err).coerceAtMost(maxOut(c))) }
            clips[clips.lastIndex] = c
        }
    }

    fun build(
        name: String, sources: List<Clip>, music: MusicTrack?, o: AutoOptions, scans: Map<String, MomentScan> = emptyMap(),
        /** Data (ms) di ogni foto e video, per la data della videocamera. */
        dates: Map<String, Long> = emptyMap(),
    ): Project {
        val style = o.style
        val rnd = Random(o.seed)
        // Battiti con la loro energia (0 calma … 1 carica), se il brano è stato analizzato.
        val beatPairs = music?.beatsUs.orEmpty().mapIndexed { i, b -> b to (music?.energy?.getOrNull(i)) }.filter { it.first > 0 }
        val beats = beatPairs.map { it.first }
        val energy = beatPairs.map { it.second }.takeIf { e -> e.isNotEmpty() && e.all { it != null } }?.map { it!! }
        // Con la musica ogni scena dura [beatsPerSlot] battiti (al ritmo del brano).
        val period = beats.zipWithNext { a, b -> b - a }.sorted().let { if (it.isEmpty()) 0L else it[it.size / 2] }
        val beatSlot = period * style.beatsPerSlot
        val minSlot = if (period in 250_000L..1_000_000L) 400_000L else 600_000L

        fun beatIndex(tUs: Long): Int { var i = beats.binarySearch(tUs); if (i < 0) i = -i - 2; return i.coerceIn(0, (beats.size - 1).coerceAtLeast(0)) }
        fun energyAt(tUs: Long): Float? = energy?.getOrNull(beatIndex(tUs))
        // "Drop": un battito dove l'energia sale di colpo rispetto agli 8 prima (l'attacco del ritornello).
        val drops: List<Long> = energy?.let { e ->
            val out = mutableListOf<Long>()
            for (i in 8 until e.size) {
                val before = (i - 8 until i).map { e[it] }.average().toFloat()
                if (e[i] >= 0.6f && e[i] - before >= 0.28f && (out.isEmpty() || beats[i] - out.last() >= period * 12)) out += beats[i]
            }
            out
        }.orEmpty()

        // 1. Durata e pezzo di ogni scena: con la musica, più corte dove il brano spinge e più lunghe dove respira.
        val clips = mutableListOf<Clip>()
        var t = 0L
        var prevMotion: MotionStyle? = null
        var videoN = 0
        val rampPresets = style.rampStyles

        /** Velocità a curva con la durata finale [out]: quanto materiale serve (null se il video è troppo corto). */
        fun rampNeed(src: Clip, preset: SpeedPreset, out: Long): Long? {
            val r = SpeedCurve.forClip(ClipSpeed.RAMP, 1_000_000L, preset.points).outputDurationUs() / 1e6
            if (r <= 0) return null
            var n = (out / r).toLong()
            repeat(3) {
                val o2 = SpeedCurve.forClip(ClipSpeed.RAMP, n, preset.points).outputDurationUs()
                if (o2 > 0) n = (n.toDouble() * out / o2).toLong()
            }
            return n.takeIf { it in 1..src.sourceDurationUs }
        }

        // 0. Intro a raffica: 8 lampi di scene diverse su mezzi battiti, poi si parte sul battito.
        var introCount = 0
        if (style.intro && o.intro && beats.size > 24 && sources.size >= 6 && period in 300_000L..1_000_000L) {
            val half = period / 2
            val ends = (1..8).map { beats.first() + it * half }
            var prev = 0L
            ends.forEachIndexed { k, end ->
                val src = sources[(k.toLong() * (sources.size - 1) / 7).toInt()]
                val len = end - prev
                val shot = if (src.isPhoto) {
                    src.copy(trimStartUs = 0, trimEndUs = len, keyframes = listOf(Keyframe(0, Transform(scale = 1.18f)), Keyframe(len, Transform(scale = 1.0f))))
                } else {
                    val n = minOf(len, src.sourceDurationUs)
                    val scan = scans[src.uri]?.takeIf { o.smart && it.samples.size >= 2 }
                    val start = scan?.let { Moments.bestStarts(it, n, 2, calm = !style.shorterOnEnergy).last() } ?: ((src.sourceDurationUs - n) * 0.55).toLong()
                    src.copy(trimStartUs = start.coerceIn(0, (src.sourceDurationUs - n).coerceAtLeast(0)), trimEndUs = start.coerceIn(0, (src.sourceDurationUs - n).coerceAtLeast(0)) + n,
                        speed = ClipSpeed.NORMAL, keyframes = emptyList())
                }
                clips += shot.copy(
                    id = java.util.UUID.randomUUID().toString(), muted = true, grade = style.grade,
                    fx = style.fx + ClipFx(FxType.RGBBEAT, 0.5f), fadeInUs = 0, fadeOutUs = 0,
                    transitionOut = if (k < 7) TransitionSpec(TransitionType.FLASH, 140_000) else TransitionSpec(style.impact.firstOrNull() ?: TransitionType.FLASH, 300_000),
                )
                prev = end
            }
            introCount = clips.size
            t = prev
        }
        val introUs = t

        // Con la musica, se le scene non bastano per la durata, si riprendono le stesse (con altri tratti) seguendo il brano:
        // così intro, drop e ritmo restano dove devono, invece di allungare tutto.
        var pass = 0
        while (true) {
        for (src in sources) {
            if (pass > 0 && t >= o.targetUs) break
            val e = energyAt(t)
            var bps = when {
                e == null -> style.beatsPerSlot
                e >= 0.72f && style.shorterOnEnergy -> (style.beatsPerSlot / 2).coerceAtLeast(1)
                e <= 0.3f -> style.beatsPerSlot * 2
                else -> style.beatsPerSlot
            }
            // Mai più corta del minimo del tema (con brani veloci si prendono più battiti).
            if (style.minSceneUs > 0 && period > 0) while (period * bps < style.minSceneUs && bps < 32) bps++
            val slot = (period * bps).takeIf { it in 350_000L..6_000_000L } ?: beatSlot
            val base = if (slot in 350_000L..6_000_000L) slot else if (src.isPhoto) style.photoUs else style.videoUs
            val ideal = if (src.isPhoto) base else minOf(base, src.sourceDurationUs)
            val maxLen = if (src.isPhoto) Clip.PHOTO_MAX_US else src.sourceDurationUs
            var len = ideal
            if (beats.size > style.beatsPerSlot) {
                // Il taglio cade sul battito più vicino alla fine ideale; se c'è un "drop" poco più in là, proprio su quello.
                val drop = drops.firstOrNull { it > t + minSlot && it <= t + (ideal * 1.6).toLong() && it - t <= maxLen }
                val end = drop ?: beatAround(beats, t + ideal, t + minSlot, t + maxLen)
                if (end != null) len = end - t
            }
            len = len.coerceIn(minOf(minSlot, maxLen), maxLen)
            val dropStart = drops.any { kotlin.math.abs(it - t) < 40_000 }
            var c = if (src.isPhoto) {
                val loud = e != null && e >= 0.6f
                // Nelle parti cariche uno scatto su ogni colpo; nei temi delicati un respiro ogni [popEvery] battiti.
                val every = if (loud) 1 else style.popEvery.coerceAtLeast(1)
                val inside = beats.filter { it > t + 60_000 && it < t + len - 60_000 }.filterIndexed { k, _ -> (k + 1) % every == 0 }
                val popScale = if (loud && !style.gentlePops) 0.07f else style.popScale
                if ((loud || style.gentlePops) && inside.size >= 1) {
                    // Parte carica: la foto fa un piccolo scatto di zoom su ogni colpo.
                    val keys = mutableListOf(Keyframe(0, Transform(scale = 1.0f)))
                    inside.forEachIndexed { k, b ->
                        val local = b - t
                        val soft = if (loud && !style.gentlePops) 90_000L else 350_000L
                        keys += Keyframe(local - 10_000, Transform(scale = 1.0f + popScale * k))
                        keys += Keyframe((local + soft).coerceAtMost(len), Transform(scale = 1.0f + popScale * (k + 1)))
                    }
                    src.copy(trimStartUs = 0, trimEndUs = len, keyframes = keys)
                } else {
                    val m = MotionStyle.varied.filter { it != prevMotion }.random(rnd)
                    prevMotion = m
                    src.copy(trimStartUs = 0, trimEndUs = len, keyframes = m.keyframes(len))
                }
            } else {
                videoN++
                // Velocità a tempo: rallenty nei momenti calmi (video a 100+ fps), accelerazioni nelle parti cariche.
                var speed = ClipSpeed.NORMAL
                var points = emptyList<SpeedPoint>()
                var need = len
                val heroNeed = if (style.hero && dropStart && src.fps >= 50f) rampNeed(src, SpeedPreset.HERO, len) else null
                if (heroNeed != null) {
                    // Momento eroe: sul drop la scena rallenta al centro.
                    speed = ClipSpeed.RAMP; points = SpeedPreset.HERO.points; need = heroNeed
                } else if (style.slowmo && src.fps >= 90f && (e == null || e < 0.55f) && rnd.nextFloat() < 0.65f && len * 0.5 <= src.sourceDurationUs) {
                    speed = ClipSpeed.SLOW; need = len / 2
                } else if (style.ramps && e != null && e >= 0.6f && videoN % style.rampEvery.coerceAtLeast(1) == 0 && src.fps >= 50f) {
                    val preset = rampPresets[(videoN / style.rampEvery.coerceAtLeast(1) + o.variant) % rampPresets.size]
                    val r = SpeedCurve.forClip(ClipSpeed.RAMP, 1_000_000L, preset.points).outputDurationUs() / 1e6
                    var n = (len / r).toLong()
                    // La curva lavora a passi: si corregge il materiale finché la durata torna col battito.
                    repeat(3) {
                        val out = SpeedCurve.forClip(ClipSpeed.RAMP, n, preset.points).outputDurationUs()
                        if (out > 0) n = (n.toDouble() * len / out).toLong()
                    }
                    if (r > 0 && n <= src.sourceDurationUs) { speed = ClipSpeed.RAMP; points = preset.points; need = n }
                }
                val scan = scans[src.uri]?.takeIf { o.smart && it.samples.size >= 2 }
                val start = if (scan != null) {
                    // Il tratto migliore (dalle proposte successive, il secondo o il terzo).
                    val best = Moments.bestStarts(scan, need, 3, calm = !style.shorterOnEnergy)
                    best[(o.variant + pass) % best.size].coerceIn(0, (src.sourceDurationUs - need).coerceAtLeast(0))
                } else {
                    val pos = if (o.variant == 0) 0.4 else 0.2 + rnd.nextDouble() * 0.45
                    ((src.sourceDurationUs - need) * pos).toLong().coerceAtLeast(0)
                }
                src.copy(trimStartUs = start, trimEndUs = start + need, speed = speed, speedPoints = points, keyframes = emptyList())
            }
            c = c.copy(muted = music != null || c.muted, fx = style.fx, fadeInUs = 0, fadeOutUs = 0, transitionOut = null, grade = style.grade)
            if (dropStart) c = c.copy(fx = c.fx + ClipFx(FxType.ZOOMBEAT, 0.8f))
            if (pass > 0) c = c.copy(id = java.util.UUID.randomUUID().toString())
            clips += c
            t += c.outputUs
        }
        pass++
        if (music == null || beats.isEmpty() || o.targetUs <= 0 || t >= o.targetUs - minSlot || pass >= 6 || sources.isEmpty()) break
        }
        if (clips.isEmpty()) return Project(name = name)
        // Durata richiesta (per esempio "come il brano"): si arriva esattamente lì (l'intro resta com'è).
        if (introCount > 0 && o.targetUs > introUs) {
            val main = clips.subList(introCount, clips.size).toMutableList()
            fit(main, o.targetUs - introUs, period, minSlot)
            while (clips.size > introCount) clips.removeAt(clips.lastIndex)
            clips += main
        } else fit(clips, o.targetUs, period, minSlot)
        // Finale: l'ultima scena, se è un video fluido, rallenta verso la fine (stessa durata).
        clips.lastOrNull()?.takeIf { !it.isPhoto && it.fps >= 50f && it.speed == ClipSpeed.NORMAL && clips.size > introCount + 1 }?.let { last ->
            rampNeed(last, SpeedPreset.SLOW_END, last.outputUs)?.let { n ->
                val start = last.trimStartUs.coerceAtMost((last.sourceDurationUs - n).coerceAtLeast(0))
                clips[clips.lastIndex] = last.copy(trimStartUs = start, trimEndUs = start + n, speed = ClipSpeed.RAMP, speedPoints = SpeedPreset.SLOW_END.points)
            }
        }

        // 2. Transizioni: d'impatto sui colpi forti e sui drop, morbide dove il brano è calmo (mai due uguali di fila).
        var prevT: TransitionType? = null
        val starts = run { var acc = 0L; clips.map { c -> acc.also { acc += c.outputUs } } }
        for (i in clips.indices) {
            if (i < introCount) continue
            var c = clips[i]
            val cutAt = starts[i] + c.outputUs
            val eCut = energyAt(cutAt)
            val isDrop = drops.any { kotlin.math.abs(it - cutAt) < 40_000 }
            if (i < clips.lastIndex) {
                val strong = style.impact.isNotEmpty() && (isDrop || (eCut != null && eCut >= 0.65f && rnd.nextFloat() < 0.6f))
                val list = if (strong) style.impact else style.transitions
                val choices = list.filter { it != prevT || it == TransitionType.CROSSFADE }.ifEmpty { list }
                val type = if (isDrop) style.impact.first() else choices.random(rnd)
                prevT = type
                val dur = if (strong) minOf(style.transitionUs, 400_000L) else style.transitionUs
                c = c.copy(transitionOut = TransitionSpec(type, minOf(dur, c.outputUs / 2, clips[i + 1].outputUs / 2)))
            }
            // Effetti a ritmo nelle parti cariche; sul drop un colpo in più.
            val eMid = energyAt(starts[i] + c.outputUs / 2)
            if (eMid != null && eMid >= 0.6f && style.beatFx.isNotEmpty()) c = c.copy(fx = c.fx.filter { f -> style.beatFx.none { it.type == f.type } } + style.beatFx)
            if (drops.any { kotlin.math.abs(it - starts[i]) < 40_000 }) c = c.copy(fx = c.fx.filter { it.type != FxType.HIT } + ClipFx(FxType.HIT, 0.8f))
            if (style.accents.isNotEmpty() && i % 3 == 1 + (o.variant % 2)) {
                val a = style.accents[(i / 3) % style.accents.size]
                c = c.copy(fx = c.fx.filter { it.type != a.type } + a)
            }
            clips[i] = c
        }
        // 3. Apertura e chiusura dal nero.
        clips[0] = clips[0].copy(fadeInUs = minOf(1_000_000L, clips[0].outputUs / 2))
        clips[clips.lastIndex] = clips.last().copy(fadeOutUs = minOf(1_500_000L, clips.last().outputUs / 2))

        // Formato e inquadratura: stesso orientamento = riempi, orientamento opposto = sfondo sfocato.
        val format = if (o.format == VideoFormat.AUTO) VideoFormat.majority(sources) else o.format
        var p = Project(name = name, clips = clips, format = format).applyFramePresets()
        if (o.smart) {
            // Orientamento opposto: si riempie il quadro seguendo i volti (o un soggetto chiaro) invece delle bande;
            // le foto senza volti si attraversano con una panoramica lenta.
            val out = p.outputOrientation()
            var dir = false
            p = p.copy(clips = p.clips.map { c ->
                if (c.orientation == out || out == Orientation.SQUARE) return@map c
                val scan = scans[c.uri]
                val clear = scan != null && (scan.hasFaces || scan.samples.count { it.hasSubject } * 2 >= scan.samples.size)
                val path = if (clear) Moments.subjectPath(scan!!) else null
                when {
                    path != null && !c.isPhoto -> c.withMode(FrameMode.FILL).copy(reframe = path)
                    c.isPhoto && path != null && scan?.hasFaces == true -> c.withMode(FrameMode.FILL).copy(reframe = path)
                    c.isPhoto -> {
                        dir = !dir
                        val (a, b) = if (dir) 0.22f to 0.78f else 0.78f to 0.22f
                        c.withMode(FrameMode.FILL).copy(reframe = Reframe(listOf(0L, c.trimmedUs), listOf(a, b), listOf(0.5f, 0.5f)))
                    }
                    else -> c
                }
            })
        }
        val total = p.durationUs

        // 4. Titolo (e sottotitolo) all'inizio, nello stile della regia.
        val texts = mutableListOf<TextItem>()
        // Con l'intro a raffica il titolo arriva sul colpo che la chiude.
        val titleAt = if (introCount > 0) introUs else 200_000L
        if (o.title.isNotBlank() && total > titleAt + TextItem.MIN_US) {
            val dur = minOf(3_500_000L, total - titleAt).coerceAtLeast(TextItem.MIN_US)
            texts += style.title.applyTo(TextItem(text = o.title.trim(), startUs = titleAt, durationUs = dur))
            if (o.subtitle.isNotBlank()) {
                texts += TextPreset.ELEGANTE.applyTo(TextItem(text = o.subtitle.trim(), startUs = titleAt + 500_000, durationUs = (dur - 500_000).coerceAtLeast(TextItem.MIN_US)))
                    .copy(size = 0.04f, y = 0.12f, endPose = null, upper = false)
            }
        }
        // Data della videocamera su ogni scena (temi vintage), dalla data vera dello scatto.
        if (style.dateStamp && dates.isNotEmpty()) {
            val fmt = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.ITALY)
            val st = p.clipStartsUs()
            p.clips.forEachIndexed { i, c ->
                val d = dates[c.uri] ?: return@forEachIndexed
                if (i < introCount || c.outputUs < TextItem.MIN_US) return@forEachIndexed
                val label = fmt.format(java.util.Date(d)).replace(".", "").uppercase(java.util.Locale.ITALY)
                // La data cambia solo quando cambia il giorno (come in un diario), la videocamera la mostra sempre.
                if (style.stamp == TextPreset.TAPPA && i > introCount && dates[p.clips[i - 1].uri]?.let { fmt.format(java.util.Date(it)) } == fmt.format(java.util.Date(d))) return@forEachIndexed
                texts += style.stamp.applyTo(TextItem(text = label, startUs = st[i], durationUs = if (style.stamp == TextPreset.TAPPA) minOf(2_500_000L, c.outputUs) else c.outputUs))
                    .let { if (p.outputOrientation() == Orientation.PORTRAIT) it.copy(x = if (style.stamp == TextPreset.TAPPA) -0.2f else 0.22f, y = 0.42f) else it }
            }
        }
        // Chiusura: una scritta negli ultimi secondi (il periodo, o il titolo), mentre l'immagine sfuma.
        val closing = o.subtitle.ifBlank { o.title }.trim()
        if (closing.isNotEmpty() && total > 9_000_000) {
            texts += style.title.applyTo(TextItem(text = closing, startUs = total - 3_000_000, durationUs = 2_800_000))
                .let { it.copy(size = it.size * 0.6f, endPose = null) }
        }
        // 5. Musica dall'inizio, sfumata in chiusura.
        val tracks = music?.let {
            // Se il video copre tutto il brano, il finale è quello del brano: basta una sfumatura breve.
            val whole = total >= it.sourceDurationUs - 300_000
            listOf(it.copy(startUs = 0, offsetUs = 0, lengthUs = minOf(it.sourceDurationUs, total), fadeInUs = 300_000,
                fadeOutUs = if (whole) minOf(600_000L, total / 3) else minOf(2_500_000L, total / 3)))
        }.orEmpty()
        p = p.copy(texts = texts, music = tracks)
        return p
    }

    /** Battito più vicino a [ideal] dentro [lo]…[hi], o null se non ce n'è. */
    private fun beatAround(beats: List<Long>, ideal: Long, lo: Long, hi: Long): Long? =
        beats.filter { it in lo..hi }.minByOrNull { kotlin.math.abs(it - ideal) }
}
