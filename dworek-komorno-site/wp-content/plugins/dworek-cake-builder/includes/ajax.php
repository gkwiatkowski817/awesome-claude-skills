<?php
/**
 * Obsluga wysylki zamowienia z kreatora (AJAX).
 * Waliduje dane, wylicza cene po stronie serwera, zapisuje zamowienie
 * jako wpis CPT `dwk_order` (modul Dworek CRM) i wysyla powiadomienia e-mail.
 *
 * @package Dworek_Cake_Builder
 */

defined( 'ABSPATH' ) || exit;

add_action( 'wp_ajax_dwk_cb_submit', 'dwk_cb_handle_submit' );
add_action( 'wp_ajax_nopriv_dwk_cb_submit', 'dwk_cb_handle_submit' );

function dwk_cb_handle_submit() {
	check_ajax_referer( 'dwk_cb_order', 'nonce' );

	$cfg = dwk_cb_get_config();

	$shape    = sanitize_key( $_POST['shape'] ?? '' );
	$size     = sanitize_key( $_POST['size'] ?? '' );
	$flavor   = sanitize_key( $_POST['flavor'] ?? '' );
	$finish   = sanitize_key( $_POST['finish'] ?? '' );
	$delivery = sanitize_key( $_POST['delivery'] ?? '' );
	$extras   = array_map( 'sanitize_key', (array) ( $_POST['extras'] ?? array() ) );
	$extras   = array_values( array_intersect( $extras, array_keys( $cfg['extras'] ) ) );

	$inscription = sanitize_text_field( wp_unslash( $_POST['inscription'] ?? '' ) );
	$inscription = mb_substr( $inscription, 0, (int) $cfg['inscription_max'] );
	$occasion    = sanitize_key( $_POST['occasion'] ?? 'inna' );
	$notes       = sanitize_textarea_field( wp_unslash( $_POST['notes'] ?? '' ) );
	$date        = sanitize_text_field( wp_unslash( $_POST['date'] ?? '' ) );
	$address     = sanitize_text_field( wp_unslash( $_POST['address'] ?? '' ) );
	$name        = sanitize_text_field( wp_unslash( $_POST['customer_name'] ?? '' ) );
	$phone       = sanitize_text_field( wp_unslash( $_POST['customer_phone'] ?? '' ) );
	$email       = sanitize_email( wp_unslash( $_POST['customer_email'] ?? '' ) );
	$transform   = sanitize_text_field( wp_unslash( $_POST['photo_transform'] ?? '' ) );
	$consent     = ! empty( $_POST['consent'] );

	// --- Walidacja ---
	$errors = array();
	if ( ! isset( $cfg['shapes'][ $shape ] ) ) {
		$errors[] = __( 'Wybierz kształt tortu.', 'dworek-cake-builder' );
	}
	if ( ! isset( $cfg['sizes'][ $size ] ) || ( $shape && $cfg['sizes'][ $size ]['shape'] !== $shape ) ) {
		$errors[] = __( 'Wybierz rozmiar pasujący do kształtu.', 'dworek-cake-builder' );
	}
	if ( ! isset( $cfg['flavors'][ $flavor ] ) ) {
		$errors[] = __( 'Wybierz smak tortu.', 'dworek-cake-builder' );
	}
	if ( ! isset( $cfg['finishes'][ $finish ] ) ) {
		$errors[] = __( 'Wybierz wykończenie tortu.', 'dworek-cake-builder' );
	}
	if ( ! isset( $cfg['delivery'][ $delivery ] ) ) {
		$errors[] = __( 'Wybierz sposób odbioru.', 'dworek-cake-builder' );
	}
	if ( ! isset( $cfg['occasions'][ $occasion ] ) ) {
		$occasion = 'inna';
	}
	if ( '' === $name || '' === $phone || ! is_email( $email ) ) {
		$errors[] = __( 'Uzupełnij poprawnie dane kontaktowe.', 'dworek-cake-builder' );
	}
	if ( ! $consent ) {
		$errors[] = __( 'Wymagana jest akceptacja regulaminu.', 'dworek-cake-builder' );
	}
	if ( isset( $cfg['delivery'][ $delivery ] ) && $cfg['delivery'][ $delivery ]['address'] && '' === $address ) {
		$errors[] = __( 'Podaj adres dostawy.', 'dworek-cake-builder' );
	}

	// Walidacja terminu z minimalnym wyprzedzeniem.
	$lead_days = ( 'express' === $delivery ) ? (int) $cfg['min_lead_days_express'] : (int) $cfg['min_lead_days'];
	$date_ts   = strtotime( $date . ' 12:00:00' );
	if ( ! $date_ts || $date_ts < strtotime( '+' . $lead_days . ' days', current_time( 'timestamp' ) - DAY_IN_SECONDS ) ) {
		$errors[] = sprintf(
			/* translators: %d - liczba dni */
			__( 'Najwcześniejszy możliwy termin to %d dni od dziś.', 'dworek-cake-builder' ),
			$lead_days
		);
	}

	if ( $errors ) {
		wp_send_json_error( array( 'message' => implode( ' ', $errors ) ) );
	}

	// --- Wgranie grafiki (opcjonalne) ---
	$photo_id = 0;
	if ( ! empty( $_FILES['photo']['name'] ) ) {
		if ( (int) $_FILES['photo']['size'] > $cfg['photo_max_mb'] * MB_IN_BYTES ) {
			wp_send_json_error( array( 'message' => __( 'Plik graficzny jest zbyt duży.', 'dworek-cake-builder' ) ) );
		}
		require_once ABSPATH . 'wp-admin/includes/file.php';
		require_once ABSPATH . 'wp-admin/includes/media.php';
		require_once ABSPATH . 'wp-admin/includes/image.php';

		$photo_id = media_handle_upload( 'photo', 0, array(), array(
			'test_form' => false,
			'mimes'     => array(
				'jpg|jpeg' => 'image/jpeg',
				'png'      => 'image/png',
				'webp'     => 'image/webp',
			),
		) );
		if ( is_wp_error( $photo_id ) ) {
			wp_send_json_error( array( 'message' => __( 'Nie udało się wgrać grafiki. Sprawdź format pliku.', 'dworek-cake-builder' ) ) );
		}
	}

	// --- Wyliczenie ceny po stronie serwera ---
	$total = (float) $cfg['sizes'][ $size ]['price']
		+ (float) $cfg['flavors'][ $flavor ]['surcharge']
		+ (float) $cfg['finishes'][ $finish ]['surcharge']
		+ (float) $cfg['delivery'][ $delivery ]['price'];
	foreach ( $extras as $extra ) {
		$total += (float) $cfg['extras'][ $extra ]['price'];
	}
	if ( $photo_id ) {
		$total += (float) $cfg['photo_price'];
	}

	// --- Zapis zamowienia (CPT z modulu Dworek CRM) ---
	$order_data = array(
		'shape'       => $shape,
		'size'        => $size,
		'flavor'      => $flavor,
		'finish'      => $finish,
		'extras'      => $extras,
		'inscription' => $inscription,
		'occasion'    => $occasion,
		'notes'       => $notes,
		'delivery'    => $delivery,
		'date'        => gmdate( 'Y-m-d', $date_ts ),
		'address'     => $address,
		'name'        => $name,
		'phone'       => $phone,
		'email'       => $email,
		'photo_id'    => $photo_id,
		'transform'   => $transform,
		'total'       => $total,
		'source'      => 'konfigurator',
	);

	if ( function_exists( 'dwk_crm_create_order' ) ) {
		$order_id = dwk_crm_create_order( $order_data );
	} else {
		// Awaryjnie (CRM nieaktywny): zapis jako szkic strony prywatnej + e-mail.
		$order_id = wp_insert_post( array(
			'post_type'    => 'post',
			'post_status'  => 'private',
			'post_title'   => 'Zamówienie tortu — ' . $name . ' — ' . gmdate( 'Y-m-d', $date_ts ),
			'post_content' => wp_json_encode( $order_data, JSON_UNESCAPED_UNICODE ),
		) );
	}

	if ( ! $order_id || is_wp_error( $order_id ) ) {
		wp_send_json_error( array( 'message' => __( 'Nie udało się zapisać zamówienia.', 'dworek-cake-builder' ) ) );
	}

	// --- Powiadomienia e-mail ---
	$summary = dwk_cb_order_summary_text( $order_data, $cfg, $order_id );
	wp_mail(
		get_option( 'admin_email' ),
		sprintf( __( 'Nowe zamówienie tortu nr %d', 'dworek-cake-builder' ), $order_id ),
		$summary
	);
	wp_mail(
		$email,
		__( 'Potwierdzenie przyjęcia zamówienia — Cukiernia Dworek Komorno', 'dworek-cake-builder' ),
		__( "Dziękujemy za złożenie zamówienia! Skontaktujemy się telefonicznie w celu potwierdzenia szczegółów i ceny.\n\n", 'dworek-cake-builder' ) . $summary
	);

	wp_send_json_success( array(
		'message' => sprintf(
			/* translators: %d - numer zamowienia */
			__( 'Dziękujemy! Twoje zamówienie nr %d zostało przyjęte. Potwierdzenie wysłaliśmy na podany adres e-mail, a wkrótce skontaktujemy się telefonicznie.', 'dworek-cake-builder' ),
			$order_id
		),
		'orderId' => $order_id,
	) );
}

/**
 * Czytelne podsumowanie zamowienia do e-maili.
 */
function dwk_cb_order_summary_text( array $d, array $cfg, $order_id ) {
	$extras_labels = array();
	foreach ( $d['extras'] as $extra ) {
		$extras_labels[] = $cfg['extras'][ $extra ]['label'];
	}

	$lines = array(
		'Zamówienie nr: ' . $order_id,
		'Kształt: ' . $cfg['shapes'][ $d['shape'] ]['label'],
		'Rozmiar: ' . $cfg['sizes'][ $d['size'] ]['label'] . ' (' . $cfg['sizes'][ $d['size'] ]['weight'] . ', ok. ' . $cfg['sizes'][ $d['size'] ]['portions'] . ' porcji)',
		'Smak: ' . $cfg['flavors'][ $d['flavor'] ]['label'],
		'Wykończenie: ' . $cfg['finishes'][ $d['finish'] ]['label'],
		'Dekoracje: ' . ( $extras_labels ? implode( ', ', $extras_labels ) : 'brak' ),
		'Własna grafika: ' . ( $d['photo_id'] ? 'tak (załącznik nr ' . $d['photo_id'] . ')' : 'nie' ),
		'Napis: ' . ( $d['inscription'] ?: 'brak' ),
		'Okazja: ' . $cfg['occasions'][ $d['occasion'] ],
		'Termin: ' . $d['date'],
		'Odbiór/dostawa: ' . $cfg['delivery'][ $d['delivery'] ]['label'],
	);
	if ( $d['address'] ) {
		$lines[] = 'Adres dostawy: ' . $d['address'];
	}
	if ( $d['notes'] ) {
		$lines[] = 'Uwagi: ' . $d['notes'];
	}
	$lines[] = 'Zamawiający: ' . $d['name'] . ', tel. ' . $d['phone'] . ', ' . $d['email'];
	$lines[] = 'Cena orientacyjna: ' . number_format_i18n( $d['total'], 2 ) . ' zł';

	return implode( "\n", $lines );
}
