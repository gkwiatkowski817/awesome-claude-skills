<?php
/**
 * Konfiguracja kreatora tortow: ksztalty, rozmiary, smaki, wykonczenia,
 * dekoracje, dostawa i cennik. Calosc mozna nadpisac filtrem `dwk_cake_config`.
 *
 * Ceny w PLN. `surcharge` to doplata do ceny bazowej rozmiaru.
 *
 * @package Dworek_Cake_Builder
 */

defined( 'ABSPATH' ) || exit;

function dwk_cb_get_config() {
	$config = array(
		'shapes' => array(
			'okragly'     => array( 'label' => 'Okrągły' ),
			'prostokatny' => array( 'label' => 'Prostokątny' ),
			'kwadratowy'  => array( 'label' => 'Kwadratowy' ),
			'serce'       => array( 'label' => 'Serce' ),
		),

		// Rozmiary przypisane do ksztaltow; po wyborze rozmiaru klient widzi
		// wage i orientacyjna liczbe porcji.
		'sizes' => array(
			'okr-18'  => array( 'shape' => 'okragly', 'label' => 'Ø 18 cm', 'weight' => '1,0 kg', 'portions' => 8, 'price' => 120 ),
			'okr-22'  => array( 'shape' => 'okragly', 'label' => 'Ø 22 cm', 'weight' => '1,5 kg', 'portions' => 12, 'price' => 160 ),
			'okr-26'  => array( 'shape' => 'okragly', 'label' => 'Ø 26 cm', 'weight' => '2,2 kg', 'portions' => 18, 'price' => 220 ),
			'okr-30'  => array( 'shape' => 'okragly', 'label' => 'Ø 30 cm', 'weight' => '3,0 kg', 'portions' => 24, 'price' => 280 ),
			'pro-2030' => array( 'shape' => 'prostokatny', 'label' => '20 × 30 cm', 'weight' => '2,5 kg', 'portions' => 20, 'price' => 240 ),
			'pro-3040' => array( 'shape' => 'prostokatny', 'label' => '30 × 40 cm', 'weight' => '4,5 kg', 'portions' => 35, 'price' => 400 ),
			'kwa-2020' => array( 'shape' => 'kwadratowy', 'label' => '20 × 20 cm', 'weight' => '1,8 kg', 'portions' => 14, 'price' => 190 ),
			'kwa-2525' => array( 'shape' => 'kwadratowy', 'label' => '25 × 25 cm', 'weight' => '2,6 kg', 'portions' => 21, 'price' => 260 ),
			'ser-24'  => array( 'shape' => 'serce', 'label' => 'Serce ~24 cm', 'weight' => '1,6 kg', 'portions' => 12, 'price' => 200 ),
		),

		'flavors' => array(
			'smietankowy'            => array( 'label' => 'Śmietankowy', 'desc' => 'Delikatny, wyjątkowo lekki tort przekładany puszystym kremem śmietankowym.', 'surcharge' => 0 ),
			'smietankowo-malinowy'   => array( 'label' => 'Śmietankowo-malinowy', 'desc' => 'Krem śmietankowy z frużeliną malinową.', 'surcharge' => 10 ),
			'smietankowo-truskawkowy' => array( 'label' => 'Śmietankowo-truskawkowy', 'desc' => 'Krem śmietankowy z frużeliną truskawkową.', 'surcharge' => 10 ),
			'smietankowo-porzeczkowy' => array( 'label' => 'Śmietankowo-porzeczkowy', 'desc' => 'Krem śmietankowy z nutą czarnej porzeczki.', 'surcharge' => 10 ),
			'smietankowo-czekoladowy' => array( 'label' => 'Śmietankowo-czekoladowy', 'desc' => 'Krem śmietankowy z kremem czekoladowym.', 'surcharge' => 10 ),
			'smiet-czekoladowo-wisniowy' => array( 'label' => 'Śmietankowo-czekoladowo-wiśniowy', 'desc' => 'Krem czekoladowy z wiśniami w likworze.', 'surcharge' => 15 ),
			'czekoladowy'            => array( 'label' => 'Czekoladowy', 'desc' => 'Kakaowe blaty z intensywnym kremem czekoladowym.', 'surcharge' => 10 ),
			'orzechowy'              => array( 'label' => 'Orzechowy', 'desc' => 'Krem orzechowy z prażonymi orzechami laskowymi.', 'surcharge' => 15 ),
		),

		'finishes' => array(
			'krem'          => array( 'label' => 'Krem śmietankowy (klasyczne wykończenie)', 'surcharge' => 0 ),
			'polewa'        => array( 'label' => 'Polewa czekoladowa', 'surcharge' => 15 ),
			'tynk'          => array( 'label' => 'Tynk maślany (gładkie wykończenie)', 'surcharge' => 25 ),
			'masa-cukrowa'  => array( 'label' => 'Masa cukrowa', 'surcharge' => 40 ),
		),

		'extras' => array(
			'owoce'      => array( 'label' => 'Świeże owoce sezonowe', 'price' => 25 ),
			'kwiaty'     => array( 'label' => 'Kwiaty cukrowe', 'price' => 30 ),
			'makaroniki' => array( 'label' => 'Makaroniki', 'price' => 25 ),
			'figurka'    => array( 'label' => 'Figurka okolicznościowa', 'price' => 35 ),
			'swieczki'   => array( 'label' => 'Świeczki urodzinowe', 'price' => 5 ),
			'zloto'      => array( 'label' => 'Jadalne złoto / posypka', 'price' => 15 ),
		),

		// Wydruk wlasnej grafiki na jadalnym oplatku.
		'photo_price' => 30,

		'occasions' => array(
			'urodziny'  => 'Urodziny',
			'imieniny'  => 'Imieniny',
			'rocznica'  => 'Rocznica',
			'chrzest'   => 'Chrzest',
			'komunia'   => 'Komunia',
			'slub'      => 'Ślub / wesele',
			'firmowa'   => 'Impreza firmowa',
			'inna'      => 'Inna okazja',
		),

		'delivery' => array(
			'odbior'  => array( 'label' => 'Odbiór osobisty — Cukiernia / Recepcja Dworku Komorno', 'price' => 0, 'address' => false ),
			'dostawa' => array( 'label' => 'Dostawa prosto do domu', 'price' => 25, 'address' => true ),
			'express' => array( 'label' => 'Dostawa ekspresowa 24h (po telefonicznym potwierdzeniu)', 'price' => 49, 'address' => true ),
		),

		// Minimalne wyprzedzenie zamowienia (dni); dla opcji express: 1 dzien.
		'min_lead_days'         => 3,
		'min_lead_days_express' => 1,
		'inscription_max'       => 60,
		'photo_max_mb'          => 8,
	);

	return apply_filters( 'dwk_cake_config', $config );
}
