<?php
/**
 * Szablon strony statycznej.
 *
 * @package Dworek_Komorno
 */

get_header();
?>
<main class="section">
	<div class="container container-narrow">
		<?php while ( have_posts() ) : the_post(); ?>
			<article <?php post_class(); ?>>
				<h1 class="page-title"><?php the_title(); ?></h1>
				<div class="entry-content"><?php the_content(); ?></div>
			</article>
		<?php endwhile; ?>
	</div>
</main>
<?php get_footer(); ?>
