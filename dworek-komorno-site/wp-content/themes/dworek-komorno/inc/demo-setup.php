<?php
/**
 * Automatyczne utworzenie struktury stron przy pierwszej aktywacji motywu.
 * Odwzorowuje strukture serwisu cukierni internetowej:
 * strona glowna, oferta, konfigurator tortu, dostawa, punkty odbioru,
 * galeria, o nas, kontakt, regulamin, polityka prywatnosci.
 *
 * @package Dworek_Komorno
 */

defined( 'ABSPATH' ) || exit;

function dwk_create_demo_pages() {
	if ( get_option( 'dwk_pages_created' ) ) {
		return;
	}

	$pages = array(
		'strona-glowna' => array(
			'title'   => 'Strona główna',
			'content' => '',
		),
		'oferta' => array(
			'title'   => 'Oferta',
			'content' => "<!-- wp:paragraph --><p>Zapraszamy do zapoznania się z ofertą naszej cukierni. Wszystkie wypieki powstają z naturalnych składników, według tradycyjnych receptur Dworku Komorno.</p><!-- /wp:paragraph -->\n<!-- wp:heading --><h2>Torty okolicznościowe</h2><!-- /wp:heading --><!-- wp:paragraph --><p>Torty urodzinowe, imieninowe, na chrzest, komunię i rocznice — w wielu kształtach, rozmiarach i smakach.</p><!-- /wp:paragraph -->\n<!-- wp:heading --><h2>Torty weselne</h2><!-- /wp:heading --><!-- wp:paragraph --><p>Nasza specjalność: od klasycznych, eleganckich tortów piętrowych po nowoczesne realizacje — monoporcje, naked cake i słodkie stoły. Każdy projekt dopasowujemy do stylu i charakteru przyjęcia.</p><!-- /wp:paragraph -->\n<!-- wp:heading --><h2>Tort z własną grafiką</h2><!-- /wp:heading --><!-- wp:paragraph --><p>Wgraj własne zdjęcie lub grafikę, a my wydrukujemy je na jadalnym opłatku. Skorzystaj z naszego <a href=\"/stworz-wlasny-tort/\">kreatora tortów</a>.</p><!-- /wp:paragraph -->",
		),
		'stworz-wlasny-tort' => array(
			'title'   => 'Stwórz własny tort',
			'content' => "<!-- wp:paragraph --><p>Skomponuj tort idealny na Twoją okazję: wybierz kształt, rozmiar, smak, wykończenie i dekoracje, dodaj własną grafikę oraz napis. Cena aktualizuje się na bieżąco.</p><!-- /wp:paragraph -->\n<!-- wp:shortcode -->[dworek_tort_konfigurator]<!-- /wp:shortcode -->",
		),
		'dostawa' => array(
			'title'   => 'Dostawa prosto do domu',
			'content' => "<!-- wp:paragraph --><p>Dowozimy torty pod wskazany adres na terenie Komorna i okolic (Kędzierzyn-Koźle, Opole i okolice — szczegóły w trakcie składania zamówienia). Tort przewozimy w warunkach chłodniczych, dzięki czemu dociera do Państwa świeży i w nienaruszonym stanie.</p><!-- /wp:paragraph -->\n<!-- wp:heading --><h2>Dostawa ekspresowa 24h</h2><!-- /wp:heading --><!-- wp:paragraph --><p>Wybrane torty możemy dostarczyć w ciągu 24 godzin od telefonicznego potwierdzenia zamówienia. Opcję dostawy ekspresowej zaznaczysz w kreatorze tortu.</p><!-- /wp:paragraph -->",
		),
		'punkty-odbioru' => array(
			'title'   => 'Punkty odbioru zamówień',
			'content' => "<!-- wp:paragraph --><p>Zamówione torty można odebrać osobiście w następujących punktach:</p><!-- /wp:paragraph -->\n<!-- wp:list --><ul><li><strong>Cukiernia Dworek Komorno</strong> — ul. Harcerska 85, Komorno</li><li><strong>Recepcja Hotelu Dworek Komorno</strong> — ul. Harcerska 85, Komorno (odbiór całodobowy po wcześniejszym uzgodnieniu)</li></ul><!-- /wp:list -->",
		),
		'galeria' => array(
			'title'   => 'Galeria',
			'content' => "<!-- wp:paragraph --><p>Wybrane realizacje naszej pracowni cukierniczej. Dodaj zdjęcia za pomocą bloku Galeria.</p><!-- /wp:paragraph -->",
		),
		'o-nas' => array(
			'title'   => 'O nas',
			'content' => "<!-- wp:paragraph --><p>Cukiernia Dworek Komorno to pracownia działająca przy hotelu i restauracji Dworek Komorno. Tworzymy wypieki z naturalnych składników, według tradycyjnych receptur — od codziennych ciast po wielopiętrowe torty weselne.</p><!-- /wp:paragraph -->\n<!-- wp:paragraph --><p>Serwis internetowy powstał z myślą o klientach, którzy chcą zamówić tort w prosty, szybki i wygodny sposób — bez wychodzenia z domu, o dowolnej porze.</p><!-- /wp:paragraph -->",
		),
		'kontakt' => array(
			'title'   => 'Kontakt',
			'content' => "<!-- wp:paragraph --><p><strong>Cukiernia Dworek Komorno</strong><br>ul. Harcerska 85, Komorno<br>tel. +48 77 403 39 19<br>e-mail: cukiernia@dworekkomorno.eu</p><!-- /wp:paragraph -->\n<!-- wp:paragraph --><p>Zamówienia online przyjmujemy całą dobę, 7 dni w tygodniu. Telefonicznie jesteśmy dostępni w godzinach otwarcia restauracji.</p><!-- /wp:paragraph -->",
		),
		'regulamin' => array(
			'title'   => 'Regulamin',
			'content' => "<!-- wp:paragraph --><p>Regulamin sklepu internetowego Cukiernia Dworek Komorno. Uzupełnij treść regulaminu we współpracy z obsługą prawną (dane sprzedawcy, zasady składania zamówień, płatności, dostawy, reklamacje, odstąpienie od umowy, dane osobowe).</p><!-- /wp:paragraph -->",
		),
		'polityka-prywatnosci' => array(
			'title'   => 'Polityka prywatności',
			'content' => "<!-- wp:paragraph --><p>Uzupełnij politykę prywatności (administrator danych, cele i podstawy przetwarzania, okres przechowywania, prawa osób, pliki cookies).</p><!-- /wp:paragraph -->",
		),
	);

	$ids = array();
	foreach ( $pages as $slug => $page ) {
		$existing = get_page_by_path( $slug );
		if ( $existing ) {
			$ids[ $slug ] = $existing->ID;
			continue;
		}
		$ids[ $slug ] = wp_insert_post( array(
			'post_type'    => 'page',
			'post_status'  => 'publish',
			'post_name'    => $slug,
			'post_title'   => $page['title'],
			'post_content' => $page['content'],
		) );
	}

	// Strona glowna jako statyczna.
	if ( ! empty( $ids['strona-glowna'] ) ) {
		update_option( 'show_on_front', 'page' );
		update_option( 'page_on_front', $ids['strona-glowna'] );
	}

	// Menu glowne.
	$menu_id = wp_create_nav_menu( 'Menu główne' );
	if ( ! is_wp_error( $menu_id ) ) {
		$menu_items = array( 'strona-glowna', 'oferta', 'stworz-wlasny-tort', 'dostawa', 'punkty-odbioru', 'galeria', 'o-nas', 'kontakt' );
		foreach ( $menu_items as $slug ) {
			if ( empty( $ids[ $slug ] ) ) {
				continue;
			}
			wp_update_nav_menu_item( $menu_id, 0, array(
				'menu-item-object-id' => $ids[ $slug ],
				'menu-item-object'    => 'page',
				'menu-item-type'      => 'post_type',
				'menu-item-status'    => 'publish',
			) );
		}
		$locations            = get_theme_mod( 'nav_menu_locations', array() );
		$locations['primary'] = $menu_id;
		set_theme_mod( 'nav_menu_locations', $locations );
	}

	update_option( 'dwk_pages_created', 1 );
}
add_action( 'after_switch_theme', 'dwk_create_demo_pages' );
